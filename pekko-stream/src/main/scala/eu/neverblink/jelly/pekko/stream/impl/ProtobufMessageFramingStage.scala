package eu.neverblink.jelly.pekko.stream.impl

import org.apache.pekko.stream.*
import org.apache.pekko.stream.scaladsl.Framing.FramingException
import org.apache.pekko.stream.stage.*
import org.apache.pekko.util.ByteString

/** A GraphStage that frames Protobuf messages based on their varint-encoded length prefix.
  *
  * This stage reads a stream of ByteStrings and emits complete (non-delimited) Protobuf messages as
  * ByteStrings. It expects the Protobuf messages to be prefixed with a varint that indicates the
  * length of the message.
  *
  * This is largely based on `DelimiterFramingStage` from Apache Pekko. Original source:
  * https://github.com/apache/pekko/blob/947ee49293dd57cb488259efac356accfb5c18d3/stream/src/main/scala/org/apache/pekko/stream/scaladsl/Framing.scala#L209
  *
  * Original license notice:
  *
  * Licensed to the Apache Software Foundation (ASF) under one or more * license agreements; and to
  * You under the Apache License, version 2.0: https://www.apache.org/licenses/LICENSE-2.0 This file
  * is part of the Apache Pekko project, which was derived from Akka.
  *
  * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
  *
  * @param maxMessageSize
  *   The maximum allowed size for a Protobuf message. If a message exceeds this size, the stage
  *   will fail.
  */
private[stream] final class ProtobufMessageFramingStage(val maxMessageSize: Int)
    extends GraphStage[FlowShape[ByteString, ByteString]]:

  val in: Inlet[ByteString] = Inlet[ByteString]("ProtobufMessageFramingStage.in")
  val out: Outlet[ByteString] = Outlet[ByteString]("ProtobufMessageFramingStage.out")
  override val shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var buffer = ByteString.empty
      private var frameSize = Int.MaxValue
      // Length of the message itself, excluding the varint prefix
      private var messageSize = 0
      // Size of the varint that encodes the length of the message
      private var lengthSize = 0

      /** push, and reset frameSize and buffer
        */
      private def pushFrame() =
        // We skip the first `lengthSize` bytes, which are the varint encoding the size of the message.
        // Unlike the original, we don't call .compact here, preserving the original ByteString structure.
        val emit = buffer.slice(lengthSize, frameSize)
        buffer = buffer.drop(frameSize)
        frameSize = Int.MaxValue
        push(out, emit)
        if (buffer.isEmpty && isClosed(in)) {
          completeStage()
        }

      /** try to push downstream, if failed then try to pull upstream
        */
      private def tryPushFrame() = {
        val buffSize = buffer.size
        if (buffSize >= frameSize) {
          pushFrame()
        } else if (buffSize >= 1) {
          if (varintDecoder(buffer.iterator)) {
            frameSize = messageSize + lengthSize
            if (messageSize > maxMessageSize) {
              failStage(
                new FramingException(
                  s"Maximum allowed Protobuf message size is $maxMessageSize but decoded delimiter " +
                    s"reported size $messageSize",
                ),
              )
            } else if (messageSize < 0) {
              failStage(
                new FramingException(
                  s"Decoded Protobuf delimiter reported negative size $messageSize",
                ),
              )
            } else if (buffSize >= frameSize) {
              pushFrame()
            } else tryPull()
          } else tryPull()
        } else tryPull()
      }

      /** Decode a varint from the given iterator.
        *
        * Based on protobuf's CodedInputStream.readRawVarint64SlowPath.
        *
        * Will store the decoded message size in `messageSize` and the length of the varint in
        * `lengthSize`.
        *
        * @param iterator
        *   Iterator of bytes to read from
        * @return
        *   true if a complete varint was read, false if not enough bytes were available
        */
      private def varintDecoder(iterator: Iterator[Byte]): Boolean =
        var result: Int = 0
        var shift: Int = 0
        var done = false
        while (!done && iterator.hasNext) {
          val b = iterator.next()
          result |= (b & 0x7f) << shift
          shift += 7
          if (b & 0x80) == 0 then {
            messageSize = result
            lengthSize = shift / 7
            done = true
          } else if shift >= 35 then {
            throw new FramingException("Delimiting varint too long (over 5 bytes)")
          }
        }
        done

      private def tryPull() = {
        if (isClosed(in)) {
          failStage(
            new FramingException(
              "Stream finished but there was a truncated final frame in the buffer",
            ),
          )
        } else pull(in)
      }

      override def onPush(): Unit = {
        buffer ++= grab(in)
        tryPushFrame()
      }

      override def onPull() = tryPushFrame()

      override def onUpstreamFinish(): Unit = {
        if (buffer.isEmpty) {
          completeStage()
        } else if (isAvailable(out)) {
          tryPushFrame()
        } // else swallow the termination and wait for pull
      }

      setHandlers(in, out, this)
    }
