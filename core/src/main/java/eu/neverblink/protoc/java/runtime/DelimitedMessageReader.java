package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Reads length-delimited messages from an input stream, one by one.
 * <p>
 * The same as calling {@link ProtoMessage#parseDelimitedFrom(InputStream, MessageFactory)} for each
 * message, but faster: each message is read into a buffer that is reused, and parsed from there.
 * Parsing from an array does not have to check for the end of a buffer or of a limit on every
 * read, as parsing from a stream does.
 *
 * @param <T> the type of the messages
 */
public final class DelimitedMessageReader<T extends ProtoMessage<T>> {

    private static final int INITIAL_BUFFER_SIZE = 8192;

    private final InputStream input;
    private final MessageFactory<T> factory;
    private byte[] buffer = new byte[INITIAL_BUFFER_SIZE];

    public DelimitedMessageReader(InputStream input, MessageFactory<T> factory) {
        this.input = input;
        this.factory = factory;
    }

    /**
     * Reads the next message.
     *
     * @return a new message, or null if the input has ended
     * @throws IOException if reading fails, or the message is truncated or invalid
     */
    public T read() throws IOException {
        final int size;
        try {
            final int firstByte = input.read();
            if (firstByte == -1) {
                return null;
            }
            size = CodedInputStream.readRawVarint32(firstByte, input);
        } catch (IOException e) {
            throw new InvalidProtocolBufferException(e);
        }
        if (size < 0) {
            throw new InvalidProtocolBufferException("Invalid message size: " + Integer.toUnsignedString(size));
        }
        int read = 0;
        while (read < size) {
            if (read == buffer.length) {
                buffer = Arrays.copyOf(buffer, (int) Math.min(size, 2L * buffer.length));
            }
            final int n = input.read(buffer, read, Math.min(size, buffer.length) - read);
            if (n < 0) {
                throw truncated(size, read);
            }
            read += n;
        }
        final var codedInput = CodedInputStream.newInstance(buffer, 0, size);
        final T msg = factory.create();
        msg.mergeFrom(codedInput, ProtoMessage.DEFAULT_MAX_RECURSION_DEPTH);
        if (codedInput.getTotalBytesRead() != size) {
            throw truncated(size, codedInput.getTotalBytesRead());
        }
        return msg;
    }

    private static InvalidProtocolBufferException truncated(int size, int read) {
        return new InvalidProtocolBufferException(
            "The message is truncated: its length prefix promises %d bytes, but the input has only %d.".formatted(
                size,
                read
            )
        );
    }
}
