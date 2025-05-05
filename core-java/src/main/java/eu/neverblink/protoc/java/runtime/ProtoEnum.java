package eu.neverblink.protoc.java.runtime;

/**
 * @author Florian Enner
 * @since 17 Nov 2019
 */
public interface ProtoEnum<E extends Enum<E>> {
    public int getNumber();

    public String getName();

    public interface EnumConverter<E extends ProtoEnum<?>> {
        /**
         * @param value The numeric wire value of the enum entry.
         * @return The enum value associated with the given numeric wire value, or null if unknown.
         */
        public E forNumber(int value);

        /**
         *
         * @param value The text representation of the enum entry
         * @return The enum value associated with the given text representation, or null if unknown.
         */
        public E forName(CharSequence value);
    }
}
