package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.OutputStream;

public final class ProtobufUtil {

    // The most values of a packed field that readPackedUInt32 makes room for up front
    private static final int MAX_PACKED_RESERVE = 1 << 16;

    /**
     * Maximum size of the output buffer used when writing messages to an OutputStream.
     * Set to 2x the default buffer size of CodedOutputStream to avoid allocating additional buffers
     * for long strings that are common in RDF.
     */
    public static final int MAX_OUTPUT_STREAM_BUFFER_SIZE = 8192;

    /**
     * Creates a CodedOutputStream with a buffer size adjusted for the message to be serialized,
     * limited to the maximum buffer size. We size the buffer to include space for the
     * size of the delimiter.
     *
     * @param outputStream the output stream to write to
     * @param messageSize  the size of the message to be written
     * @return a new CodedOutputStream instance
     */
    public static CodedOutputStream createCodedOutputStream(OutputStream outputStream, int messageSize) {
        final int bufferSize = Integer.min(
            CodedOutputStream.computeUInt32SizeNoTag(messageSize) + messageSize,
            MAX_OUTPUT_STREAM_BUFFER_SIZE
        );
        return CodedOutputStream.newInstance(outputStream, bufferSize);
    }

    /**
     * Creates a CodedOutputStream with a default (maximum) buffer size. Use this method when
     * you want to reuse the CodedOutputStream for multiple messages and you don't know the
     * size of the messages in advance.
     *
     * @param outputStream the output stream to write to
     * @return a new CodedOutputStream instance with the maximum buffer size
     */
    public static CodedOutputStream createCodedOutputStream(OutputStream outputStream) {
        return CodedOutputStream.newInstance(outputStream, MAX_OUTPUT_STREAM_BUFFER_SIZE);
    }

    /**
     * Writes a packed repeated uint32 field WITHOUT the field tag: the length delimiter
     * followed by the values. The caller is responsible for writing the field tag first.
     *
     * @param output the output to write to
     * @param values the values to write
     * @throws IOException if an error occurred writing to {@code output}
     */
    public static void writePackedUInt32(CodedOutputStream output, RepeatedInt values) throws IOException {
        final int[] array = values.array();
        final int size = values.size();
        int dataSize = 0;
        for (int i = 0; i < size; i++) {
            dataSize += CodedOutputStream.computeUInt32SizeNoTag(array[i]);
        }
        output.writeUInt32NoTag(dataSize);
        for (int i = 0; i < size; i++) {
            output.writeUInt32NoTag(array[i]);
        }
    }

    /**
     * Reads a packed repeated uint32 field, assuming the field tag was already consumed.
     *
     * @param input the input to read from
     * @param store the store to add the values to
     * @throws IOException if an error occurred reading from {@code input}
     */
    public static void readPackedUInt32(CodedInputStream input, RepeatedInt store) throws IOException {
        final int length = input.readRawVarint32();
        final int oldLimit = input.pushLimit(length);
        // Every value takes at least one byte, so this is room for all of them, instead of growing
        // the array many times. Capped: a stream decoder does not know yet if the input really
        // has that many bytes.
        store.reserve(Math.min(length, MAX_PACKED_RESERVE));
        // Straight into the array: the values are read as fast as the varints can be decoded
        int[] values = store.values;
        int size = store.size;
        while (!input.isAtEnd()) {
            if (size == values.length) {
                store.size = size;
                store.reserve(1);
                values = store.values;
            }
            values[size++] = input.readUInt32();
        }
        store.size = size;
        if (input.getBytesUntilLimit() > 0) {
            // isAtEnd() is also true where a stream ends: there, it ended within the field
            throw new InvalidProtocolBufferException(
                "While parsing a protocol message, the input ended unexpectedly in the middle of a field."
            );
        }
        input.popLimit(oldLimit);
    }

    /**
     * Reads a non-packed repeated uint32 field, assuming {@code tag} was already consumed.
     *
     * @param input the input to read from
     * @param store the store to add the values to
     * @param tag the tag of the field being read
     * @return the next tag in the stream
     * @throws IOException if an error occurred reading from {@code input}
     */
    public static int readRepeatedUInt32(CodedInputStream input, RepeatedInt store, int tag) throws IOException {
        int nextTag;
        do {
            store.add(input.readUInt32());
        } while ((nextTag = input.readTag()) == tag);
        return nextTag;
    }

    /**
     * Reads a repeated string field, assuming {@code tag} was already consumed.
     *
     * @param input the input to read from
     * @param store the store to add the values to
     * @param tag the tag of the field being read
     * @return the next tag in the stream
     * @throws IOException if an error occurred reading from {@code input}
     */
    public static int readRepeatedString(CodedInputStream input, RepeatedString store, int tag) throws IOException {
        int nextTag;
        do {
            store.add(input.readString());
        } while ((nextTag = input.readTag()) == tag);
        return nextTag;
    }
}
