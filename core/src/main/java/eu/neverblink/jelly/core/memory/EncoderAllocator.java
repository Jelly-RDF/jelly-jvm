package eu.neverblink.jelly.core.memory;

import eu.neverblink.jelly.core.proto.v1.RdfQuad;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;

/**
 * Allocator for RdfTriple and RdfQuad objects in {@link eu.neverblink.jelly.core.ProtoEncoder}.
 * If you can guarantee that the proto objects are only created temporarily, immediately serialized,
 * and never touched again, you can use the arena allocator, which will reuse the objects
 * after .releaseAll() is called. It will significantly reduce the heap pressure, potentially
 * improving performance.
 */
public abstract class EncoderAllocator {

    /**
     * Create a new {@link RdfTriple.Mutable} object.
     * @return triple
     */
    public abstract RdfTriple.Mutable newTriple();

    /**
     * Create a new {@link RdfQuad.Mutable} object.
     * @return quad
     */
    public abstract RdfQuad.Mutable newQuad();

    /**
     * Release all allocated objects. This is a no-op for the heap allocator.
     * After this is called, you MUST NOT reuse any of the previously allocated objects.
     */
    public abstract void releaseAll();

    /**
     * Simple, thread-safe allocator that uses the Java heap to allocate objects.
     * @return a new instance of {@link EncoderAllocator} that allocates objects on the heap.
     */
    public static EncoderAllocator newHeapAllocator() {
        return HEAP_ALLOCATOR;
    }

    /**
     * Arena-based allocator (on-heap) that can reuse objects after .releaseAll() is called.
     * This is NOT thread-safe, and it is your responsibility to ensure that the you don't
     * use allocated objects after calling .releaseAll(). In that case, things will break
     * very badly.
     * @param maxSize maximum size of the arena. After this size is reached, remaining objects will
     *                be allocated on the heap.
     * @return a new instance of {@link EncoderAllocator} that allocates objects in a heap-backed arena.
     */
    public static EncoderAllocator newArenaAllocator(int maxSize) {
        return new ArenaAllocator(maxSize);
    }

    private static final EncoderAllocator HEAP_ALLOCATOR = new EncoderAllocator() {
        @Override
        public RdfTriple.Mutable newTriple() {
            return RdfTriple.newInstance();
        }

        @Override
        public RdfQuad.Mutable newQuad() {
            return RdfQuad.newInstance();
        }

        @Override
        public void releaseAll() {
            // No-op
        }
    };

    private static class ArenaAllocator extends EncoderAllocator {

        private final ArenaMessageAllocator<RdfTriple.Mutable> tripleAllocator;
        private final ArenaMessageAllocator<RdfQuad.Mutable> quadAllocator;

        public ArenaAllocator(int maxSize) {
            this.tripleAllocator = new ArenaMessageAllocator<>(RdfTriple::newInstance, maxSize);
            this.quadAllocator = new ArenaMessageAllocator<>(RdfQuad::newInstance, maxSize);
        }

        @Override
        public RdfTriple.Mutable newTriple() {
            return tripleAllocator.newInstance();
        }

        @Override
        public RdfQuad.Mutable newQuad() {
            return quadAllocator.newInstance();
        }

        @Override
        public void releaseAll() {
            tripleAllocator.releaseAll();
            quadAllocator.releaseAll();
        }
    }
}
