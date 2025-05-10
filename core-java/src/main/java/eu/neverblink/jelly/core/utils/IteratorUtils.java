package eu.neverblink.jelly.core.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Utility class for working with iterators.
 * <p>
 * This class provides methods to transform and manipulate iterators in a functional style.
 */
public class IteratorUtils {

    private IteratorUtils() {}

    /**
     * Maps an iterator to another type using the provided mapper function.
     *
     * @param iterator the iterator to map
     * @param mapper the mapping function
     * @param <T> the input type
     * @param <R> the output type
     * @return a new iterator that applies the mapping function to each element
     */
    public static <T, R> Iterator<R> map(Iterator<T> iterator, Function<T, R> mapper) {
        return new MappingIterator<>(iterator, mapper);
    }

    /**
     * Concatenates two iterators into a single iterator.
     *
     * @param a the first iterator
     * @param b the second iterator
     * @param <T> the type of elements in the iterators
     * @return a new iterator that yields elements from both iterators in sequence
     */
    public static <T> Iterator<T> concat(Iterator<T> a, Iterator<T> b) {
        return new ConcatenatingIterator<>(a, b);
    }

    /**
     * Creates an iterator that yields a single element.
     *
     * @param element the element to yield
     * @param <T> the type of the element
     * @return an iterator that yields the specified element
     */
    public static <T> Iterator<T> of(T element) {
        return new SingleElementIterator<>(element);
    }

    // Helper class to implement the mapping iterator
    private record MappingIterator<T, R>(Iterator<T> iterator, Function<T, R> mapper) implements Iterator<R> {
        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public R next() {
            return mapper.apply(iterator.next());
        }
    }

    // Helper class to implement the concatenating iterator
    private static class ConcatenatingIterator<T> implements Iterator<T> {

        private final Iterator<T> first;
        private final Iterator<T> second;
        private boolean isFirstDone = false;

        public ConcatenatingIterator(Iterator<T> first, Iterator<T> second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public boolean hasNext() {
            if (!isFirstDone && first.hasNext()) {
                return true;
            }
            isFirstDone = true;
            return second.hasNext();
        }

        @Override
        public T next() {
            if (!isFirstDone && first.hasNext()) {
                return first.next();
            }
            return second.next();
        }
    }

    // Helper class to implement the single element iterator
    private static class SingleElementIterator<T> implements Iterator<T> {

        private final T element;
        private boolean hasNext = true;

        public SingleElementIterator(T element) {
            this.element = element;
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public T next() {
            if (!hasNext) {
                throw new NoSuchElementException("No more elements");
            }
            hasNext = false;
            return element;
        }
    }
}
