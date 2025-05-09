package eu.neverblink.jelly.core.utils;

/**
 * FlatteningConverter is a functional interface that converts a single entity into collection of elements.
 *
 * @param <T> the type of the entity to be converted
 * @param <R> the type of the elements in the collection
 */
@FunctionalInterface
public interface FlatteningConverter<T, R> {
    /**
     * Converts a single entity into a collection of elements.
     *
     * @param entity the entity to be converted
     * @return the collection of elements
     */
    Iterable<R> convert(T entity);
}
