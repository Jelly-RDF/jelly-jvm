package eu.neverblink.jelly.convert.rdf4j;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.utils.QuadMaker;
import eu.neverblink.jelly.core.utils.TripleMaker;
import org.eclipse.rdf4j.model.*;

/**
 * Internal base class for RDF4J decoder converters. Do not extend or use directly.
 */
@InternalApi
public abstract class BaseRdf4jDecoderConverter
    implements ProtoDecoderConverter<Value, Rdf4jDatatype>, TripleMaker<Value, Statement>, QuadMaker<Value, Statement>
{

    protected final ValueFactory vf;

    protected BaseRdf4jDecoderConverter(final ValueFactory vf) {
        this.vf = vf;
    }

    public final ValueFactory getValueFactory() {
        return vf;
    }

    @Override
    public final Value makeTripleNode(Value s, Value p, Value o) {
        try {
            // RDF4J doesn't accept generalized statements (unlike Jena) which is why we need to do a type cast here.
            return vf.createTriple((Resource) s, (IRI) p, o);
        } catch (ClassCastException e) {
            throw new RdfProtoDeserializationError(
                "Cannot create generalized triple node with %s, %s, %s".formatted(s, p, o),
                e
            );
        }
    }

    @Override
    public final Statement makeQuad(Value subject, Value predicate, Value object, Value graph) {
        try {
            return vf.createStatement((Resource) subject, (IRI) predicate, object, (Resource) graph);
        } catch (ClassCastException e) {
            throw new RdfProtoDeserializationError(
                "Cannot create generalized quad with %s, %s, %s, %s".formatted(subject, predicate, object, graph),
                e
            );
        }
    }

    @Override
    public final Statement makeTriple(Value subject, Value predicate, Value object) {
        try {
            return vf.createStatement((Resource) subject, (IRI) predicate, object);
        } catch (ClassCastException e) {
            throw new RdfProtoDeserializationError(
                "Cannot create generalized triple with %s, %s, %s".formatted(subject, predicate, object),
                e
            );
        }
    }
}
