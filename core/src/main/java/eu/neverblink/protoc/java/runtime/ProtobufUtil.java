package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class ProtobufUtil {

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
     * Writes a packed repeated uint64 field WITHOUT the field tag: the length delimiter
     * followed by the values. The caller is responsible for writing the field tag first.
     *
     * @param output the output to write to
     * @param values the values to write
     * @throws IOException if an error occurred writing to {@code output}
     */
    public static void writePackedUInt64(CodedOutputStream output, RepeatedLong values) throws IOException {
        final long[] array = values.array();
        final int size = values.size();
        int dataSize = 0;
        for (int i = 0; i < size; i++) {
            dataSize += CodedOutputStream.computeUInt64SizeNoTag(array[i]);
        }
        output.writeUInt32NoTag(dataSize);
        for (int i = 0; i < size; i++) {
            output.writeUInt64NoTag(array[i]);
        }
    }

    /**
     * Reads a packed repeated uint64 field, assuming the field tag was already consumed.
     *
     * @param input the input to read from
     * @param store the store to add the values to
     * @throws IOException if an error occurred reading from {@code input}
     */
    public static void readPackedUInt64(CodedInputStream input, RepeatedLong store) throws IOException {
        final int length = input.readRawVarint32();
        final int oldLimit = input.pushLimit(length);
        while (input.getBytesUntilLimit() > 0) {
            store.add(input.readUInt64());
        }
        input.popLimit(oldLimit);
    }

    /**
     * Reads a non-packed repeated uint64 field, assuming {@code tag} was already consumed.
     *
     * @param input the input to read from
     * @param store the store to add the values to
     * @param tag the tag of the field being read
     * @return the next tag in the stream
     * @throws IOException if an error occurred reading from {@code input}
     */
    public static int readRepeatedUInt64(CodedInputStream input, RepeatedLong store, int tag) throws IOException {
        int nextTag;
        do {
            store.add(input.readUInt64());
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
