package eu.neverblink.jelly.core.internal.utils;

/**
 * Not thread-safe lazy property holder.
 * @param <T> type of the property
 */
public class LazyProperty<T> {

    private T value;
    private final LazyInitializer<T> initializer;

    public LazyProperty(LazyInitializer<T> initializer) {
        this.initializer = initializer;
    }

    public T provide() {
        if (value == null) {
            value = initializer.initialize();
        }
        return value;
    }

    @FunctionalInterface
    public interface LazyInitializer<T> {
        T initialize();
    }
}
