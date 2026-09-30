package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Writes length-delimited messages to an output stream, one after another.
 * <p>
 * The same as calling {@link ProtoMessage#writeDelimitedTo(CodedOutputStream)} for each message
 * on one {@link CodedOutputStream}, but faster: each message is serialized into a byte array with
 * protobuf's array encoder, which does not have to check for the end of a buffer on every write,
 * as the stream encoder does. The array collects messages until the next one does not fit, and is
 * then written to the stream in one call.
 * <p>
 * Made with a {@link CodedOutputStream} instead of an {@link OutputStream}, it hands each message
 * to that stream as soon as it is serialized, in one {@link CodedOutputStream#writeRawBytes} call.
 * That costs one more copy of the message, but keeps its bytes in order with anything else written
 * to the same {@link CodedOutputStream}.
 * <p>
 * The array is as large as the largest message written so far, and at least
 * {@link #INITIAL_BUFFER_SIZE}.
 */
public final class DelimitedMessageWriter {

    private static final int INITIAL_BUFFER_SIZE = 8192;

    // One of the two is null
    private final OutputStream output;
    private final CodedOutputStream codedOutput;
    private byte[] buffer = new byte[INITIAL_BUFFER_SIZE];
    private int position = 0;

    public DelimitedMessageWriter(OutputStream output) {
        this.output = output;
        this.codedOutput = null;
    }

    public DelimitedMessageWriter(CodedOutputStream codedOutput) {
        this.output = null;
        this.codedOutput = codedOutput;
    }

    /**
     * Writes a message with its length before it. Nothing may change the message during the call.
     *
     * @param message the message
     * @throws IOException if writing to the stream fails
     */
    public void write(ProtoMessage<?> message) throws IOException {
        final int size = message.getSerializedSize();
        final int total = CodedOutputStream.computeUInt32SizeNoTag(size) + size;
        if (total > buffer.length - position) {
            writeBuffer();
            if (total > buffer.length) {
                buffer = Arrays.copyOf(buffer, Math.max(total, 2 * buffer.length));
            }
        }
        final CodedOutputStream coded = CodedOutputStream.newInstance(buffer, position, total);
        coded.writeUInt32NoTag(size);
        message.writeTo(coded);
        coded.checkNoSpaceLeft();
        position += total;
        if (codedOutput != null) {
            writeBuffer();
        }
    }

    /**
     * Writes out the messages that are still in the array, and flushes the stream. A
     * {@link CodedOutputStream} is flushed into its own stream, which is not flushed.
     *
     * @throws IOException if writing to the stream fails
     */
    public void flush() throws IOException {
        writeBuffer();
        if (codedOutput != null) {
            codedOutput.flush();
        } else {
            output.flush();
        }
    }

    private void writeBuffer() throws IOException {
        if (position > 0) {
            if (codedOutput != null) {
                codedOutput.writeRawBytes(buffer, 0, position);
            } else {
                output.write(buffer, 0, position);
            }
            position = 0;
        }
    }
}
