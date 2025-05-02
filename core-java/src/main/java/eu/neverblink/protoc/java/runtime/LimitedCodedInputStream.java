package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedInputStream;

import java.io.InputStream;

/**
 * Wrapper for {@link CodedInputStream} which limits the recursion depth.
 */
public final class LimitedCodedInputStream {
    private final CodedInputStream in;
    private int recursionDepth;
    private final int maxRecursionDepth;
    private static final int DEFAULT_RECURSION_DEPTH = 64;

    public LimitedCodedInputStream(CodedInputStream input, int maxRecursionDepth) {
        this.in = input;
        this.maxRecursionDepth = maxRecursionDepth;
        this.recursionDepth = 0;
    }

    public LimitedCodedInputStream(CodedInputStream input) {
        this(input, DEFAULT_RECURSION_DEPTH);
    }

    public void incrementRecursionDepth() {
        recursionDepth++;
    }

    public void decrementRecursionDepth() {
        recursionDepth--;
    }

    public void checkRecursionDepth() {
        if (recursionDepth > maxRecursionDepth) {
            throw new RuntimeException("Maximum recursion depth exceeded: " + recursionDepth);
        }
    }

    public CodedInputStream in() {
        return in;
    }

    public static LimitedCodedInputStream newInstance(InputStream input, int sizeLimit) {
        InputStream limitedInput = new LimitedInputStream(input, sizeLimit);
        return new LimitedCodedInputStream(CodedInputStream.newInstance(limitedInput));
    }
}
