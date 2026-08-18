package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes protobuf messages to an {@link OutputStream} through a reusable {@code byte[]}.
 * <p>
 * Each message is first serialized into an internal buffer and then handed to the output stream in
 * a single {@code write(byte[], int, int)} call. The buffer is kept between messages, so a stream
 * of similarly sized messages allocates once.
 * <p>
 * The point of this is the encoder protobuf picks. Serializing into a {@code byte[]} uses
 * {@code CodedOutputStream.ArrayEncoder}, which is markedly faster than the
 * {@code OutputStreamEncoder} you get from {@code CodedOutputStream.newInstance(OutputStream)}:
 * it does not check for available space on every small varint, does not maintain a running byte
 * counter, and does not reserve worst-case space (3 bytes per char) when writing strings. That
 * last one matters a lot for RDF, where long literals would otherwise force protobuf onto a slow
 * path that allocates a fresh {@code byte[3 * length]} per string.
 * <p>
 * This works because the exact serialized size of a message is always known up front
 * ({@link ProtoMessage#getSerializedSize()}), so the buffer can be sized exactly and running out
 * of space is impossible.
 * <p>
 * This class does NOT call {@link ProtoMessage#resetCachedSize()}. If you reuse and mutate one
 * message object between writes, you must do that yourself before calling in here.
 * <p>
 * Not thread-safe.
 */
public final class BufferedProtoWriter {

    /**
     * Default cap on the internal buffer, 1 MiB. Messages larger than this are written through the
     * streaming path instead, so that a single huge message cannot pin a huge buffer forever.
     */
    public static final int DEFAULT_MAX_BUFFER_SIZE = 1024 * 1024;

    /**
     * Smallest buffer we will allocate. Below this the slack calculation is not worth the bother.
     */
    private static final int MIN_BUFFER_SIZE = 256;

    private final OutputStream outputStream;
    private final int maxBufferSize;

    private byte[] buffer = null;

    /**
     * @param outputStream the stream to write to
     */
    public BufferedProtoWriter(OutputStream outputStream) {
        this(outputStream, DEFAULT_MAX_BUFFER_SIZE);
    }

    /**
     * @param outputStream  the stream to write to
     * @param maxBufferSize cap on the internal buffer, in bytes. Messages that do not fit under
     *                      the cap are written through the streaming path instead.
     */
    public BufferedProtoWriter(OutputStream outputStream, int maxBufferSize) {
        if (outputStream == null) {
            throw new NullPointerException("outputStream must not be null");
        }
        if (maxBufferSize < 1) {
            throw new IllegalArgumentException("maxBufferSize must be positive, got: " + maxBufferSize);
        }
        this.outputStream = outputStream;
        this.maxBufferSize = maxBufferSize;
    }

    /**
     * @return the stream this writer writes to
     */
    public OutputStream getOutputStream() {
        return outputStream;
    }

    /**
     * Writes the message in length-delimited form: a varint with the message size, then the
     * message itself.
     *
     * @param message the message to write
     * @throws IOException if an error occurred writing to the underlying stream
     */
    public void writeDelimited(ProtoMessage<?> message) throws IOException {
        // [X] Ensure that the serialized size is cached
        final int messageSize = message.getSerializedSize();
        final int totalSize = CodedOutputStream.computeUInt32SizeNoTag(messageSize) + messageSize;
        if (!ensureCapacity(totalSize)) {
            // Too big for the buffer – fall back to streaming this one message out directly.
            final var codedOutput = ProtobufUtil.createCodedOutputStream(outputStream, messageSize);
            codedOutput.writeUInt32NoTag(messageSize);
            message.writeTo(codedOutput);
            // Flush right away, so that bytes stay in order with anything written after this.
            codedOutput.flush();
            return;
        }
        final var codedOutput = CodedOutputStream.newInstance(buffer, 0, totalSize);
        codedOutput.writeUInt32NoTag(messageSize);
        message.writeTo(codedOutput);
        codedOutput.checkNoSpaceLeft();
        outputStream.write(buffer, 0, totalSize);
    }

    /**
     * Writes the message without a length delimiter.
     *
     * @param message the message to write
     * @throws IOException if an error occurred writing to the underlying stream
     */
    public void write(ProtoMessage<?> message) throws IOException {
        // [X] Ensure that the serialized size is cached
        final int messageSize = message.getSerializedSize();
        if (!ensureCapacity(messageSize)) {
            final var codedOutput = ProtobufUtil.createCodedOutputStream(outputStream, messageSize);
            message.writeTo(codedOutput);
            codedOutput.flush();
            return;
        }
        final var codedOutput = CodedOutputStream.newInstance(buffer, 0, messageSize);
        message.writeTo(codedOutput);
        codedOutput.checkNoSpaceLeft();
        outputStream.write(buffer, 0, messageSize);
    }

    /**
     * Flushes the underlying output stream. Nothing is held back in the internal buffer between
     * calls, so this only forwards the flush.
     *
     * @throws IOException if an error occurred flushing the underlying stream
     */
    public void flush() throws IOException {
        outputStream.flush();
    }

    /**
     * Makes sure the buffer can hold {@code needed} bytes, allocating or growing it if necessary.
     * The buffer is never shrunk.
     * <p>
     * When we do (re)allocate, we round the request up to the next power of two, so that the small
     * frame-to-frame size differences that are normal in RDF streams don't cause a realloc on every
     * frame. A power of two was chosen over a fixed multiplier because it is cheap to compute and
     * gives a stable size that repeated similar frames converge on. The result is always clamped
     * to the configured cap.
     *
     * @param needed number of bytes the message needs
     * @return true if the buffer can be used, false if the message is over the cap and the caller
     *         should fall back to streaming
     */
    private boolean ensureCapacity(int needed) {
        if (buffer != null && buffer.length >= needed) {
            return true;
        }
        if (needed > maxBufferSize) {
            return false;
        }
        int newSize = Integer.highestOneBit(Math.max(needed - 1, MIN_BUFFER_SIZE - 1)) << 1;
        if (newSize > maxBufferSize || newSize < needed) {
            // Overshot the cap, or overflowed int (highestOneBit << 1 on a value above 2^30).
            newSize = maxBufferSize;
        }
        buffer = new byte[newSize];
        return true;
    }
}
