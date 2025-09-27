package eu.neverblink.jelly.convert.rdf4j;

import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.base.CoreDatatype;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public final class Rdf4jDecoderConverter extends BaseRdf4jDecoderConverter {

    /**
     * Creates a new Rdf4jDecoderConverter.
     * <p>
     * This converter uses the {@link SimpleValueFactory} to create RDF4J values. You can override this by
     * used the one-parameter constructor that takes a {@link ValueFactory} instance.
     */
    public Rdf4jDecoderConverter() {
        super(SimpleValueFactory.getInstance());
    }

    /**
     * Creates a new Rdf4jDecoderConverter with a custom ValueFactory.
     *
     * @param vf the ValueFactory to use for creating RDF4J values
     */
    public Rdf4jDecoderConverter(ValueFactory vf) {
        super(vf);
    }

    @Override
    public Value makeSimpleLiteral(String lex) {
        return vf.createLiteral(lex);
    }

    @Override
    public Value makeLangLiteral(String lex, String lang) {
        return vf.createLiteral(lex, lang);
    }

    @Override
    public Value makeDtLiteral(String lex, Rdf4jDatatype dt) {
        return vf.createLiteral(lex, dt.dt(), dt.coreDatatype());
    }

    @Override
    public Rdf4jDatatype makeDatatype(String dt) {
        var iri = vf.createIRI(dt);
        final var coreDatatype = CoreDatatype.from(iri);
        if (coreDatatype != CoreDatatype.NONE) {
            // If it's a core datatype, use the core IRI to allow for reference equality checks.
            iri = coreDatatype.getIri();
        }
        return new Rdf4jDatatype(iri, coreDatatype);
    }

    @Override
    public Value makeBlankNode(String label) {
        return vf.createBNode(label);
    }

    @Override
    public Value makeIriNode(String iri) {
        return vf.createIRI(iri);
    }

    @Override
    public Value makeDefaultGraphNode() {
        return null;
    }
}
