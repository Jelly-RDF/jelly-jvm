package eu.ostrzyciel.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;

public interface TitaniumJellyEncoder extends RdfQuadConsumer {
    
    // TODO: factory methods

    /**
     * Returns the number of rows currently in the encoded row buffer.
     * @return int
     */
    int getRowCount();

    /**
     * Returns the rows in the encoded row buffer as a Scala collection and clears the buffer.
     * @return scala.collection.immutable.Iterable<RdfStreamRow>
     */
    scala.collection.immutable.Iterable<RdfStreamRow> getRowsScala();
    
    /**
     * Returns the rows in the encoded row buffer as a Java collection and clears the buffer.
     * @return java.util.Iterable<RdfStreamRow>
     */
    Iterable<RdfStreamRow> getRowsJava();
}
