package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import eu.neverblink.jelly.core.InternalApi;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

/**
 * Abstract interface implemented by Protocol Message objects.
 * <p>
 * API partially copied from Google's MessageNano
 *
 * @author Florian Enner
 * @author Piotr Sowiński
 */
public abstract class ProtoMessage<MessageType extends ProtoMessage<?>> {

    /**
     * Default maximum recursion depth for parsing messages.
     * This is used to prevent stack overflow errors when parsing deeply nested messages.
     */
    public static final int DEFAULT_MAX_RECURSION_DEPTH = 64;

    protected ProtoMessage() {}

    /**
     * Copies all fields and data from another message of the same
     * type into this message.
     *
     * @param other message with the contents to be copied
     * @return this
     */
    public abstract MessageType copyFrom(MessageType other);

    /**
     * Get the number of bytes required to encode this message.
     * Returns the cached size or calls computeSerializedSize which
     * sets the cached size.
     * WARNING: once you call this method, the cached size is set and will be persisted unless
     * the message is modified with mergeFrom or copyFrom. If you modify the message with setters,
     * the cached size may be stale. Instead, you should call .clone() and work on a new instance.
     *
     * @return the size of the serialized proto form
     */
    public abstract int getSerializedSize();

    /**
     * Computes the number of bytes required to encode this message. This does
     * not update the cached size.
     *
     * @return the size of the serialized proto form
     */
    protected abstract int computeSerializedSize();

    @InternalApi
    protected abstract int getCachedSize();

    public abstract void resetCachedSize();

    /**
     * Serializes the message and writes it to {@code output}.
     * It is the caller's responsibility to ensure that getSerializedSize() was called
     * before this method. In this class, this is marked with "[X]" in comments.
     *
     * @param output the output to receive the serialized form.
     * @throws IOException if an error occurred writing to {@code output}.
     */
    public abstract void writeTo(CodedOutputStream output) throws IOException;

    /**
     * Serializes the message and writes it to the {@code output} in
     * length delimited form.
     *
     * @return this
     */
    public final MessageType writeDelimitedTo(CodedOutputStream output) throws IOException {
        // [X] Ensure that the serialized size is cached
        output.writeUInt32NoTag(getSerializedSize());
        this.writeTo(output);
        return getThis();
    }

    public final MessageType writeDelimitedTo(OutputStream output) throws IOException {
        // [X] Ensure that the serialized size is cached
        final int size = getSerializedSize();
        final var codedOutput = ProtobufUtil.createCodedOutputStream(output, size);
        codedOutput.writeUInt32NoTag(size);
        writeTo(codedOutput);
        codedOutput.flush();
        return getThis();
    }

    public final MessageType writeTo(OutputStream output) throws IOException {
        // [X] Ensure that the serialized size is cached
        final int size = getSerializedSize();
        final var codedOutput = ProtobufUtil.createCodedOutputStream(output, size);
        writeTo(codedOutput);
        codedOutput.flush();
        return getThis();
    }

    /**
     * Parses the contents of {@code input} as a message of this type, in NON-DELIMITED form.
     * @param input the input stream to read the message from
     * @param factory the factory to create the message
     * @return a new message parsed from the input stream
     * @param <T> the type of the message
     * @throws IOException if an error occurred reading from {@code input}
     */
    public static <T extends ProtoMessage<T>> T parseFrom(InputStream input, MessageFactory<T> factory)
        throws IOException {
        final var msg = factory.create();
        final var cin = CodedInputStream.newInstance(input);
        msg.mergeFrom(cin, DEFAULT_MAX_RECURSION_DEPTH);
        return msg;
    }

    /**
     * Parses the contents for one message written in length delimited form.
     *
     * @return a new message parsed from the input stream or null if there is no message to parse.
     */
    public static <T extends ProtoMessage<T>> T parseDelimitedFrom(InputStream input, MessageFactory<T> factory)
        throws IOException {
        int size;
        try {
            int firstByte = input.read();
            if (firstByte == -1) {
                return null;
            }
            size = CodedInputStream.readRawVarint32(firstByte, input);
        } catch (IOException e) {
            throw new InvalidProtocolBufferException(e);
        }
        final var msg = factory.create();
        final var codedInput = CodedInputStream.newInstance(new LimitedInputStream(input, size));
        msg.mergeFrom(codedInput, DEFAULT_MAX_RECURSION_DEPTH);
        return msg;
    }

    /**
     * Parse {@code input} as a message of this type and merge it with the
     * message being built.
     *
     * @return last read tag or 0 if the end of the message was reached.
     */
    @InternalApi
    public abstract int mergeFrom(CodedInputStream input, int remainingDepth) throws IOException;

    /**
     * Merge {@code other} into the message being built. {@code other} must have the exact same type
     * as {@code this}.
     *
     * <p>Merging occurs as follows. For each field:<br>
     * * For singular primitive fields, if the field is set in {@code other}, then {@code other}'s
     * value overwrites the value in this message.<br>
     * * For singular message fields, if the field is set in {@code other}, it is merged into the
     * corresponding sub-message of this message using the same merging rules.<br>
     * * For repeated fields, the elements in {@code other} are concatenated with the elements in
     * this message.<br>
     * * For oneof groups, if the other message has one of the fields set, the group of this message
     * is cleared and replaced by the field of the other message, so that the oneof constraint is
     * preserved.
     *
     * <p>This is equivalent to the {@code Message::MergeFrom} method in C++.
     *
     * @return this
     */
    public abstract MessageType mergeFrom(MessageType other);

    /**
     * Serialize to a byte array.
     *
     * @return byte array with the serialized data.
     */
    public final byte[] toByteArray() {
        return ProtoMessage.toByteArray(this);
    }

    /**
     * Serialize to a byte array with a length-delimiter prepended.
     * <p>
     * If you are writing to an internal buffer before sending the data over the network,
     * this should be slightly more efficient than writing to a ByteArrayOutputStream.
     *
     * @return byte array with the serialized data and a length-delimiter prepended.
     */
    public final byte[] toByteArrayDelimited() {
        // [X] Ensure that the serialized size is cached
        final int messageSize = getSerializedSize();
        final int delimiterSize = CodedOutputStream.computeUInt32SizeNoTag(messageSize);
        final byte[] result = new byte[delimiterSize + messageSize];
        final CodedOutputStream output = CodedOutputStream.newInstance(result);
        try {
            output.writeUInt32NoTag(messageSize);
            this.writeTo(output);
            output.checkNoSpaceLeft();
        } catch (IOException e) {
            throw new RuntimeException("Serializing to a byte array threw an IOException (should never happen).", e);
        }
        return result;
    }

    /**
     * Serialize to a byte array.
     *
     * @return byte array with the serialized data.
     */
    public static byte[] toByteArray(ProtoMessage<?> msg) {
        final byte[] result = new byte[msg.getSerializedSize()];
        toByteArray(msg, result, 0, result.length);
        return result;
    }

    /**
     * Serialize to a byte array starting at offset through length. The
     * method getSerializedSize must have been called prior to calling
     * this method so the proper length is know.  If an attempt to
     * write more than length bytes OutOfSpaceException will be thrown
     * and if length bytes are not written then IllegalStateException
     * is thrown.
     * [X] Ensure that the serialized size is cached -- done by caller.
     */
    public static void toByteArray(ProtoMessage<?> msg, byte[] data, int offset, int length) {
        try {
            final CodedOutputStream output = CodedOutputStream.newInstance(data, offset, length);
            msg.writeTo(output);
            output.checkNoSpaceLeft();
        } catch (IOException e) {
            throw new RuntimeException(
                "Serializing to a byte array threw an IOException " + "(should never happen).",
                e
            );
        }
    }

    /**
     * Parse {@code data} as a message of this type and merge it with the message being built.
     */
    public static <T extends ProtoMessage<T>> T mergeFrom(T msg, final byte[] data)
        throws InvalidProtocolBufferException {
        return mergeFrom(msg, data, 0, data.length);
    }

    /**
     * Parse {@code data} as a message of this type and merge it with the message being built.
     */
    public static <T extends ProtoMessage<T>> T mergeFrom(T msg, final byte[] data, final int off, final int len)
        throws InvalidProtocolBufferException {
        try {
            final var input = CodedInputStream.newInstance(data, off, len);
            return mergeFrom(msg, input, DEFAULT_MAX_RECURSION_DEPTH);
        } catch (InvalidProtocolBufferException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Reading from a byte array threw an IOException (should never happen).");
        }
    }

    /**
     * Parse {@code input} as a message of this type and merge it with the message being built.
     */
    @InternalApi
    public static <T extends ProtoMessage<T>> T mergeFrom(T msg, CodedInputStream input, int remainingDepth)
        throws IOException {
        if (msg.mergeFrom(input, remainingDepth - 1) != 0) {
            throw new InvalidProtocolBufferException("Protocol message end-group tag did not match expected tag.");
        }
        return msg;
    }

    @InternalApi
    public static <T extends ProtoMessage<T>> void mergeDelimitedFrom(
        T msg,
        CodedInputStream input,
        int remainingDepth
    ) throws IOException {
        if (remainingDepth < 0) {
            throw new RuntimeException("Maximum recursion depth exceeded");
        }
        final int length = input.readRawVarint32();
        final int oldLimit = input.pushLimit(length);
        if (msg.mergeFrom(input, remainingDepth - 1) != 0) {
            throw new InvalidProtocolBufferException("Protocol message end-group tag did not match expected tag.");
        }
        input.popLimit(oldLimit);
    }

    @InternalApi
    protected static <T extends ProtoMessage<T>> int computeRepeatedMessageSizeNoTag(final Collection<T> values) {
        int dataSize = 0;
        for (final ProtoMessage<?> value : values) {
            int valSize = value.getSerializedSize();
            dataSize += CodedOutputStream.computeUInt32SizeNoTag(valSize) + valSize;
        }
        return dataSize;
    }

    @InternalApi
    protected static <T extends ProtoMessage<T>> int readRepeatedMessage(
        final MessageCollection<T, ?> store,
        final CodedInputStream input,
        final int tag,
        final int remainingDepth
    ) throws IOException {
        int nextTag;
        do {
            final var msg = store.appendMessage();
            mergeDelimitedFrom(msg, input, remainingDepth);
        } while ((nextTag = input.readTag()) == tag);
        return nextTag;
    }

    /**
     * Clears all fields in this message and resets the cached size.
     * @return this
     */
    public abstract MessageType clear();

    /**
     * Indicates whether another object is "equal to" this one.
     * <p>
     * An object is considered equal when it is of the same message
     * type, contains the same fields (same has state), and all the
     * field contents are equal.
     * <p>
     * This comparison ignores unknown fields, so the serialized binary
     * form may not be equal.
     *
     * @param obj the reference object with which to compare
     * @return {@code true} if this object is the same as the obj
     * argument; {@code false} otherwise.
     */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * Creates a new instance of this message with the same content
     */
    @Override
    public abstract MessageType clone();

    @SuppressWarnings("unchecked")
    private MessageType getThis() {
        return (MessageType) this;
    }
}
