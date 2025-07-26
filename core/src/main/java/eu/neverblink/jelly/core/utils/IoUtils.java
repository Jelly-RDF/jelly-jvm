package eu.neverblink.jelly.core.utils;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import eu.neverblink.protoc.java.runtime.MessageFactory;
import eu.neverblink.protoc.java.runtime.ProtoMessage;
import java.io.*;
import java.util.function.Consumer;

public final class IoUtils {

    private static final int DEFAULT_INPUT_STREAM_BUFFER_SIZE = 8192;

    private IoUtils() {}

    public record AutodetectDelimitingResponse(boolean isDelimited, InputStream newInput) {}

    /**
     * Autodetects whether the input stream is a non-delimited Jelly file or a delimited Jelly file.
     * <p>
     * To do this, the first three bytes in the stream are peeked.
     * These bytes are then put back into the stream, and the stream is returned, so the parser won't notice the peeking.
     * @param inputStream the input stream
     * @return (isDelimited, newInputStream) where isDelimited is true if the stream is a delimited Jelly file
     * @throws IOException if an I/O error occurs
     */
    public static AutodetectDelimitingResponse autodetectDelimiting(InputStream inputStream) throws IOException {
        final var scout = inputStream.readNBytes(3);
        final var scoutIn = new ByteArrayInputStream(scout);
        final var newInput = new SequenceInputStream(scoutIn, inputStream);

        // Truth table (notation: 0A = 0x0A, NN = not 0x0A, ?? = don't care):
        // NN ?? ?? -> delimited (all non-delimited start with 0A)
        // 0A NN ?? -> non-delimited
        // 0A 0A NN -> delimited (total message size = 10)
        // 0A 0A 0A -> non-delimited (stream options size = 10)

        // A case like "0A 0A 0A 0A" in the delimited variant is impossible. It would mean that the whole message
        // is 10 bytes long, while stream options alone are 10 bytes long.

        // It's not possible to have a long varint starting with 0A, because its most significant bit
        // would have to be 1 (continuation bit). So, we don't need to worry about that case.

        // Yeah, it's magic. But it works.

        final var isDelimited = scout.length == 3 && (scout[0] != 0x0A || (scout[1] == 0x0A && scout[2] != 0x0A));
        return new AutodetectDelimitingResponse(isDelimited, newInput);
    }

    /**
     * Utility method to transform a non-delimited Jelly frame (as a byte array) into a delimited one,
     * writing it to a byte stream.
     * <p>
     * This is useful if you for example store non-delimited frames in a database, but want to write them to a stream.
     *
     * @param nonDelimitedFrame EXACTLY one non-delimited Jelly frame
     * @param output the output stream to write the frame to
     * @throws IOException if an I/O error occurs
     */
    public static void writeFrameAsDelimited(byte[] nonDelimitedFrame, OutputStream output) throws IOException {
        // Don't worry, the buffer won't really have 0-size. It will be of minimal size able to fit the varint.
        final var codedOutput = CodedOutputStream.newInstance(output, 0);
        codedOutput.writeUInt32NoTag(nonDelimitedFrame.length);
        codedOutput.flush();
        output.write(nonDelimitedFrame);
    }

    /**
     * Functional interface for processing frames from an input stream.
     * @param <TFrame> the type of the frame
     */
    @FunctionalInterface
    @Deprecated(since = "3.4.0", forRemoval = true)
    public interface FrameProcessor<TFrame> {
        TFrame apply(InputStream inputStream) throws IOException;
    }

    /**
     * Reads a stream of frames from an input stream and processes each frame using the provided frame processor.
     * @param inputStream the input stream to read from
     * @param frameProcessor the function to process each frame
     * @param frameConsumer the consumer to handle each processed frame
     * @param <TFrame> the type of the frame
     */
    @Deprecated(since = "3.4.0", forRemoval = true)
    public static <TFrame> void readStream(
        InputStream inputStream,
        FrameProcessor<TFrame> frameProcessor,
        Consumer<TFrame> frameConsumer
    ) throws IOException {
        while (true) {
            final var maybeFrame = frameProcessor.apply(inputStream);
            if (maybeFrame == null) {
                // No more frames available, break the loop
                break;
            }

            frameConsumer.accept(maybeFrame);
        }
    }

    /**
     * Reads a stream of delimited protobuf messages (frames) from an input stream. Each frame
     * is passed to the provided consumer for processing.
     * <p>
     * This method reads frames in a delimited format, where each frame is preceded by its size as a varint.
     * Internally, it uses a single `CodedInputStream` to read the frames efficiently.
     *
     * @param inputStream the input stream to read from
     * @param messageFactory the factory to create new frames
     * @param frameConsumer the consumer to handle each processed frame
     * @param <TFrame> the type of the frame
     * @throws IOException if an I/O error occurs
     */
    public static <TFrame extends ProtoMessage<TFrame>> void readStream(
        InputStream inputStream,
        MessageFactory<TFrame> messageFactory,
        Consumer<TFrame> frameConsumer
    ) throws IOException {
        final var codedInput = CodedInputStream.newInstance(inputStream, DEFAULT_INPUT_STREAM_BUFFER_SIZE);
        while (!codedInput.isAtEnd()) {
            final int frameSize = codedInput.readRawVarint32();
            if (frameSize < 0) {
                throw new InvalidProtocolBufferException("Invalid frame size: " + frameSize);
            }
            // Discard the current limit (it's always Integer.MAX_VALUE) and set a new one for the frame size
            codedInput.pushLimit(frameSize);
            final var frame = messageFactory.create();
            frame.mergeFrom(codedInput, ProtoMessage.DEFAULT_MAX_RECURSION_DEPTH);
            // Reset the size counter to avoid integer overflows
            codedInput.resetSizeCounter();
            // Pop the limit to be able to read the next frame's size
            codedInput.popLimit(Integer.MAX_VALUE);
            frameConsumer.accept(frame);
        }
    }
}
