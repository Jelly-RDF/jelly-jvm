package eu.neverblink.jelly.jmh;

import java.io.InputStream;
import java.util.Objects;

/**
 * {@link java.io.ByteArrayInputStream} without the locking.
 * <p>
 * Every method of ByteArrayInputStream is {@code synchronized}. Readers that pull one byte at a
 * time (Thrift, RDF4J's binary format, the length prefix of delimited protobuf messages) then spend
 * up to half of their time taking that lock, which measures the input source rather than the format.
 * Readers get the bytes from this instead, so that the source costs next to nothing for all of them.
 */
public final class UnsyncByteArrayInputStream extends InputStream {

    private final byte[] buf;
    private int pos;
    private int mark;

    public UnsyncByteArrayInputStream(byte[] buf) {
        this.buf = buf;
    }

    @Override
    public int read() {
        return pos < buf.length ? buf[pos++] & 0xff : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        if (pos >= buf.length) {
            return len == 0 ? 0 : -1;
        }
        final int n = Math.min(len, buf.length - pos);
        System.arraycopy(buf, pos, b, off, n);
        pos += n;
        return n;
    }

    @Override
    public long skip(long n) {
        final long skipped = Math.max(0, Math.min(n, buf.length - pos));
        pos += (int) skipped;
        return skipped;
    }

    @Override
    public int available() {
        return buf.length - pos;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void mark(int readLimit) {
        mark = pos;
    }

    @Override
    public void reset() {
        pos = mark;
    }
}
