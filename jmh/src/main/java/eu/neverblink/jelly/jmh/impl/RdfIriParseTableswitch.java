package eu.neverblink.jelly.jmh.impl;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import eu.neverblink.protoc.java.runtime.LimitedCodedInputStream;
import eu.neverblink.protoc.java.runtime.MessageFactory;
import eu.neverblink.protoc.java.runtime.ProtoMessage;
import java.io.IOException;
import java.io.InputStream;

public abstract class RdfIriParseTableswitch extends ProtoMessage<RdfIriParseTableswitch> implements Cloneable {

    /**
     * An empty instance of this message type.
     */
    public static final RdfIriParseTableswitch EMPTY = newInstance();

    /**
     * <code>optional uint32 prefix_id = 1;</code>
     */
    protected int prefixId;

    /**
     * <code>optional uint32 name_id = 2;</code>
     */
    protected int nameId;

    protected int cachedSize = -1;

    private RdfIriParseTableswitch() {}

    /**
     * @return a new empty instance of {@code Mutable}
     */
    public static RdfIriParseTableswitch.Mutable newInstance() {
        return new RdfIriParseTableswitch.Mutable();
    }

    /**
     * <code>optional uint32 prefix_id = 1;</code>
     * @return the prefixId
     */
    public int getPrefixId() {
        return prefixId;
    }

    /**
     * <code>optional uint32 name_id = 2;</code>
     * @return the nameId
     */
    public int getNameId() {
        return nameId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RdfIriParseTableswitch)) {
            return false;
        }
        RdfIriParseTableswitch other = (RdfIriParseTableswitch) o;
        return prefixId == other.prefixId && nameId == other.nameId;
    }

    @Override
    public final void writeTo(final CodedOutputStream output) throws IOException {
        if (prefixId != 0) {
            output.writeRawByte((byte) 8);
            output.writeUInt32NoTag(prefixId);
        }
        if (nameId != 0) {
            output.writeRawByte((byte) 16);
            output.writeUInt32NoTag(nameId);
        }
    }

    @Override
    public final int getSerializedSize() {
        if (cachedSize < 0) {
            cachedSize = computeSerializedSize();
        }
        return cachedSize;
    }

    @Override
    protected final int computeSerializedSize() {
        int size = 0;
        if (prefixId != 0) {
            size += 1 + CodedOutputStream.computeUInt32SizeNoTag(prefixId);
        }
        if (nameId != 0) {
            size += 1 + CodedOutputStream.computeUInt32SizeNoTag(nameId);
        }
        return size;
    }

    @Override
    public final int getCachedSize() {
        return cachedSize;
    }

    /**
     * Resets the cached size of this message.
     * Call this method if you modify the message after it was serialized.
     * NOTE: this is a SHALLOW operation! It will not reset the size of nested messages.
     */
    @Override
    public final void resetCachedSize() {
        cachedSize = -1;
    }

    @Override
    public final RdfIriParseTableswitch.Mutable clone() {
        return newInstance().copyFrom(this);
    }

    /**
     * Parse this message in NON-delimited form from a byte array.
     * This assumes that the message spans the entire array.
     */
    public static RdfIriParseTableswitch parseFrom(final byte[] data) throws InvalidProtocolBufferException {
        return ProtoMessage.mergeFrom(newInstance(), data);
    }

    /**
     * Parse this message in NON-delimited form from a {@link LimitedCodedInputStream}.
     * This assumes that the message spans the entire input stream.
     */
    public static RdfIriParseTableswitch parseFrom(final LimitedCodedInputStream input) throws IOException {
        return ProtoMessage.mergeFrom(newInstance(), input);
    }

    /**
     * Parse this message in NON-delimited form from a Java {@link InputStream}.
     * This assumes that the message spans the entire input stream.
     */
    public static RdfIriParseTableswitch parseFrom(final InputStream input) throws IOException {
        return ProtoMessage.parseFrom(input, RdfIriParseTableswitch.getFactory());
    }

    /**
     * Parse this message in DELIMITED form from a Java {@link InputStream}.
     * If there is no message to be read, null will be returned.
     * To read all delimited messages in the stream, call this method
     * repeatedly until null is returned.
     */
    public static RdfIriParseTableswitch parseDelimitedFrom(final InputStream input) throws IOException {
        return ProtoMessage.parseDelimitedFrom(input, RdfIriParseTableswitch.getFactory());
    }

    /**
     * @return factory for creating RdfIriFastParse messages
     */
    public static MessageFactory<RdfIriParseTableswitch> getFactory() {
        return RdfIriParseTableswitch.RdfIriFactory.INSTANCE;
    }

    /**
     * @return this type's descriptor.
     */
    public static Descriptors.Descriptor getDescriptor() {
        return null;
    }

    private enum RdfIriFactory implements MessageFactory<RdfIriParseTableswitch> {
        INSTANCE;

        @Override
        public final RdfIriParseTableswitch create() {
            return RdfIriParseTableswitch.newInstance();
        }
    }

    /**
     * Mutable subclass of the parent class.
     * You can call setters on this class to set the values.
     * When passing the constructed message to the serializer,
     * you should use the parent class (using .asImmutable()) to
     * ensure the message won't be modified by accident.
     */
    public static final class Mutable extends RdfIriParseTableswitch {

        private Mutable() {}

        /**
         * <code>optional uint32 prefix_id = 1;</code>
         * @param value the prefixId to set
         * @return this
         */
        public Mutable setPrefixId(final int value) {
            prefixId = value;
            return this;
        }

        /**
         * <code>optional uint32 name_id = 2;</code>
         * @param value the nameId to set
         * @return this
         */
        public Mutable setNameId(final int value) {
            nameId = value;
            return this;
        }

        @Override
        public final Mutable copyFrom(final RdfIriParseTableswitch other) {
            cachedSize = other.cachedSize;
            prefixId = other.prefixId;
            nameId = other.nameId;
            return this;
        }

        @Override
        public final Mutable mergeFrom(final RdfIriParseTableswitch other) {
            cachedSize = -1;
            setPrefixId(other.prefixId);
            setNameId(other.nameId);
            return this;
        }

        @Override
        @SuppressWarnings("fallthrough")
        public final Mutable mergeFrom(final LimitedCodedInputStream inputLimited) throws IOException {
            final CodedInputStream input = inputLimited.in();
            while (true) {
                int tag = input.readTag();
                switch (tag >> 3) {
                    case 1: {
                        // prefixId
                        prefixId = input.readUInt32();
                        break;
                    }
                    case 2: {
                        // nameId
                        nameId = input.readUInt32();
                        break;
                    }
                    case 0: {
                        return this;
                    }
                    default: {
                        if (!input.skipField(tag)) {
                            return this;
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public final Mutable clear() {
            prefixId = 0;
            nameId = 0;
            cachedSize = -1;
            return this;
        }

        /**
         * Returns this message as an immutable message, without any copies.
         */
        public RdfIriParseTableswitch asImmutable() {
            return this;
        }
    }
}
