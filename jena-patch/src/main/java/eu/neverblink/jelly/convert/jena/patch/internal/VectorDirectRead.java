package eu.neverblink.jelly.convert.jena.patch.internal;

import eu.neverblink.jelly.core.InternalApi;
import java.util.Vector;

@InternalApi
public class VectorDirectRead<E> extends Vector<E> {

    /**
     * Returns the internal array of the vector without copying it. Do not modify the array!
     * The array may be larger than the current size of the vector, use .size() to determine the actual size.
     * <p>
     * This method makes sense only if you are absolutely sure that the vector is not modified
     * after this call, as it returns the internal array directly.
     *
     * @return the internal array of the vector
     */
    public Object[] getArrayUnsafe() {
        return elementData;
    }
}
