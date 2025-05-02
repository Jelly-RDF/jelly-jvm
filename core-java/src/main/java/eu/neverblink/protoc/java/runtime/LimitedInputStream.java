package eu.neverblink.protoc.java.runtime;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An InputStream implementations which reads from some other InputStream but is limited to a
 * particular number of bytes. Used by mergeDelimitedFrom(). This is intentionally
 * package-private so that UnknownFieldSet can share it.
 *
 * @author kenton@google.com Kenton Varda
 */
final class LimitedInputStream extends FilterInputStream {
    private int limit;

    LimitedInputStream(InputStream in, int limit) {
        super(in);
        this.limit = limit;
    }

    @Override
    public int available() throws IOException {
        return Math.min(super.available(), limit);
    }

    @Override
    public int read() throws IOException {
        if (limit <= 0) {
            return -1;
        }
        final int result = super.read();
        if (result >= 0) {
            --limit;
        }
        return result;
    }

    @Override
    public int read(final byte[] b, final int off, int len) throws IOException {
        if (limit <= 0) {
            return -1;
        }
        len = Math.min(len, limit);
        final int result = super.read(b, off, len);
        if (result >= 0) {
            limit -= result;
        }
        return result;
    }

    @Override
    public long skip(final long n) throws IOException {
        // because we take the minimum of an int and a long, result is guaranteed to be
        // less than or equal to Integer.MAX_INT so this cast is safe
        int result = (int) super.skip(Math.min(n, limit));
        if (result >= 0) {
            // if the superclass adheres to the contract for skip, this condition is always true
            limit -= result;
        }
        return result;
    }
}
