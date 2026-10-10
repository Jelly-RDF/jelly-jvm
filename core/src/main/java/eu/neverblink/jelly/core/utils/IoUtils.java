package eu.neverblink.jelly.core.utils;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
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
     * To do this, the first bytes in the stream are peeked: usually one, and at most 128.
     * These bytes are then put back into the stream, and the stream is returned, so the parser won't notice the peeking.
     * <p>
     * In very rare cases the answer is wrong: a non-delimited frame whose first field is not the
     * stream options, but, e.g., the frame metadata, can by chance also be a valid delimited
     * stream. Frames written by Jelly-JVM never have this problem: their fields are in
     * field-number order, so the first frame of a stream starts with the stream options. If you
     * write frames some other way, and you know whether they are delimited, do not rely on the
     * detection.
     * @param inputStream the input stream
     * @return (isDelimited, newInputStream) where isDelimited is true if the stream is a delimited Jelly file
     * @throws IOException if an I/O error occurs
     */
    public static AutodetectDelimitingResponse autodetectDelimiting(InputStream inputStream) throws IOException {
        return autodetectDelimiting(inputStream, RdfStreamFrame.getDescriptor());
    }

    /**
     * Autodetects whether the input stream is a single non-delimited frame or a sequence of
     * delimited frames, for frames of the given message type. See
     * {@link #autodetectDelimiting(InputStream)}.
     * <p>
     * A non-delimited frame starts with the tag of one of its fields – any of them, as writers
     * may write the fields in any order. A delimited stream starts with the size of the first
     * frame. If the first byte is not a tag of the frame, the stream is delimited. Otherwise, it
     * may also be the size of a small first frame, of at most 127 bytes: then the stream is
     * delimited if the bytes after it make up exactly that many bytes of frame fields. Some bytes
     * are valid both ways, see {@link #autodetectDelimiting(InputStream)}.
     *
     * @param inputStream the input stream
     * @param frameType the descriptor of the frame message, e.g.,
     *                  {@code SparqlResultsFrame.getDescriptor()} for Jelly-SPARQL
     * @return (isDelimited, newInputStream) where isDelimited is true if the stream is delimited
     * @throws IOException if an I/O error occurs
     */
    public static AutodetectDelimitingResponse autodetectDelimiting(
        InputStream inputStream,
        Descriptors.Descriptor frameType
    ) throws IOException {
        final byte[] scout = inputStream.readNBytes(1);
        if (scout.length == 0) {
            // An empty input: an empty non-delimited frame
            return new AutodetectDelimitingResponse(false, withScout(scout, inputStream));
        }
        final boolean[] frameTags = frameTags(frameType);
        final int first = scout[0] & 0xFF;
        if (first >= frameTags.length || !frameTags[first]) {
            // Not a tag of the frame, so the size of the first frame. This includes 0, an empty
            // frame: a stream of only empty delimited frames is delimited too.
            return new AutodetectDelimitingResponse(true, withScout(scout, inputStream));
        }
        // The first byte could be both a tag of the frame and the size of a small delimited frame.
        // Read the whole frame that it would be the size of.
        final byte[] rest = inputStream.readNBytes(first);
        final byte[] all = new byte[1 + rest.length];
        all[0] = scout[0];
        System.arraycopy(rest, 0, all, 1, rest.length);
        final boolean isDelimited = all.length == first + 1 && isFrameFields(all, 1, first + 1, frameTags);
        return new AutodetectDelimitingResponse(isDelimited, withScout(all, inputStream));
    }

    private static InputStream withScout(byte[] scout, InputStream rest) {
        return new SequenceInputStream(new ByteArrayInputStream(scout), rest);
    }

    /**
     * The single-byte tags (field number 1 to 15) that the fields of a frame can have: indexed by
     * the tag, true for the valid ones. A repeated scalar field may be packed or not, so both of
     * its tags are valid.
     */
    private static boolean[] frameTags(Descriptors.Descriptor frameType) {
        final boolean[] tags = new boolean[128];
        for (final Descriptors.FieldDescriptor field : frameType.getFields()) {
            final int number = field.getNumber();
            if (number > 15) {
                continue;
            }
            final int wireType = switch (field.getType()) {
                case MESSAGE, STRING, BYTES, GROUP -> 2;
                case DOUBLE, FIXED64, SFIXED64 -> 1;
                case FLOAT, FIXED32, SFIXED32 -> 5;
                default -> 0;
            };
            tags[(number << 3) | wireType] = true;
            if (field.isRepeated()) {
                tags[(number << 3) | 2] = true;
            }
        }
        return tags;
    }

    /** Whether bytes [from, to) are exactly a sequence of fields with the given tags. */
    private static boolean isFrameFields(byte[] bytes, int from, int to, boolean[] frameTags) {
        int pos = from;
        while (pos < to) {
            final int tag = bytes[pos++] & 0xFF;
            if (tag >= frameTags.length || !frameTags[tag]) {
                return false;
            }
            switch (tag & 7) {
                case 0 -> {
                    while (pos < to && (bytes[pos] & 0x80) != 0) {
                        pos++;
                    }
                    pos++;
                }
                case 1 -> pos += 8;
                case 5 -> pos += 4;
                default -> {
                    // Length-delimited. The length must fit in the bytes we have, so it is short.
                    long length = 0;
                    int shift = 0;
                    boolean ended = false;
                    while (pos < to && shift < 35) {
                        final int b = bytes[pos++] & 0xFF;
                        length |= (long) (b & 0x7F) << shift;
                        shift += 7;
                        if ((b & 0x80) == 0) {
                            ended = true;
                            break;
                        }
                    }
                    if (!ended || length > to - pos) {
                        return false;
                    }
                    pos += (int) length;
                }
            }
        }
        return pos == to;
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
