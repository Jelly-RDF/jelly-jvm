package eu.neverblink.jelly.jmh.caches;

/**
 * Stand-in for NodeEncoderImpl.DependentNode, which is package-private and therefore not reachable
 * from here. Only the field layout matters – the caches under test never look inside it, but its
 * allocation size shows up in the GC profile.
 */
public final class DependentNode {

    public Object encoded;
    public int lookupPointer1;
    public int lookupSerial1;
    public int lookupPointer2;
    public int lookupSerial2;
}
