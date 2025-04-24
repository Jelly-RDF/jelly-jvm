package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.proto.v1.RdfDefaultGraph;
import eu.neverblink.jelly.core.proto.v1.RdfGraphEnd;
import eu.neverblink.jelly.core.proto.v1.RdfGraphStart;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfQuad;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;

/**
 * Represents RDF terms in a type-safe manner with conversion capabilities to and from Protocol Buffer messages.
 * This interface defines the hierarchy of RDF terms and provides factory methods for creating terms from proto messages.
 */
public sealed interface RdfTerm {
    /**
     * Creates an IRI term from a Protocol Buffer RDF IRI message.
     * @param iri The Protocol Buffer RDF IRI message
     * @return An Iri instance, or null if the input is null
     */
    static Iri from(RdfIri iri) {
        if (iri == null) {
            return null;
        }

        return new Iri(iri.getPrefixId(), iri.getNameId());
    }

    /**
     * Creates a blank node term from a string identifier.
     * @param bNode The blank node identifier
     * @return A BNode instance, or null if the input is null
     */
    static BNode from(String bNode) {
        if (bNode == null) {
            return null;
        }

        return new BNode(bNode);
    }

    /**
     * Creates a literal term from a Protocol Buffer RDF literal message.
     * @param literal The Protocol Buffer RDF literal message
     * @return A LiteralTerm instance (SimpleLiteral, LanguageLiteral, or DtLiteral), or null if the input is null
     */
    static LiteralTerm from(RdfLiteral literal) {
        if (literal == null) {
            return null;
        }

        if (literal.hasLangtag()) {
            return new LanguageLiteral(literal.getLex(), literal.getLangtag());
        } else if (literal.hasDatatype()) {
            return new DtLiteral(literal.getLex(), literal.getDatatype());
        } else {
            return new SimpleLiteral(literal.getLex());
        }
    }

    /**
     * Creates a triple term from a Protocol Buffer RDF triple message.
     * @param triple The Protocol Buffer RDF triple message
     * @return A Triple instance, or null if the input is null
     */
    static Triple from(RdfTriple triple) {
        if (triple == null) {
            return null;
        }

        final var subject =
            switch (triple.getSubjectCase()) {
                case S_IRI -> from(triple.getSIri());
                case S_BNODE -> from(triple.getSBnode());
                case S_LITERAL -> from(triple.getSLiteral());
                case S_TRIPLE_TERM -> from(triple.getSTripleTerm());
                case SUBJECT_NOT_SET -> null;
            };

        final var predicate =
            switch (triple.getPredicateCase()) {
                case P_IRI -> from(triple.getPIri());
                case P_BNODE -> from(triple.getPBnode());
                case P_LITERAL -> from(triple.getPLiteral());
                case P_TRIPLE_TERM -> from(triple.getPTripleTerm());
                case PREDICATE_NOT_SET -> null;
            };

        final var object =
            switch (triple.getObjectCase()) {
                case O_IRI -> from(triple.getOIri());
                case O_BNODE -> from(triple.getOBnode());
                case O_LITERAL -> from(triple.getOLiteral());
                case O_TRIPLE_TERM -> from(triple.getOTripleTerm());
                case OBJECT_NOT_SET -> null;
            };

        return new Triple(subject, predicate, object);
    }

    /**
     * Creates a graph start marker from a Protocol Buffer RDF graph start message.
     * @param graphStart The Protocol Buffer RDF graph start message
     * @return A GraphStart instance, or null if the input is null
     */
    static GraphStart from(RdfGraphStart graphStart) {
        if (graphStart == null) {
            return null;
        }

        final var graph =
            switch (graphStart.getGraphCase()) {
                case G_IRI -> from(graphStart.getGIri());
                case G_BNODE -> from(graphStart.getGBnode());
                case G_DEFAULT_GRAPH -> from(graphStart.getGDefaultGraph());
                case G_LITERAL -> from(graphStart.getGLiteral());
                case GRAPH_NOT_SET -> null;
            };

        return new GraphStart(graph);
    }

    /**
     * Creates a graph end marker from a Protocol Buffer RDF graph end message.
     * @param ignoredGraphEnd The Protocol Buffer RDF graph end message (ignored)
     * @return A new GraphEnd instance
     */
    static GraphEnd from(RdfGraphEnd ignoredGraphEnd) {
        return new GraphEnd();
    }

    /**
     * Creates a default graph marker from a Protocol Buffer RDF default graph message.
     * @param ignoredDefaultGraph The Protocol Buffer RDF default graph message (ignored)
     * @return A new DefaultGraph instance
     */
    static DefaultGraph from(RdfDefaultGraph ignoredDefaultGraph) {
        return new DefaultGraph();
    }

    /**
     * Creates a quad term from a Protocol Buffer RDF quad message.
     * @param quad The Protocol Buffer RDF quad message
     * @return A Quad instance, or null if the input is null
     */
    static Quad from(RdfQuad quad) {
        if (quad == null) {
            return null;
        }

        final var subject =
            switch (quad.getSubjectCase()) {
                case S_IRI -> from(quad.getSIri());
                case S_BNODE -> from(quad.getSBnode());
                case S_LITERAL -> from(quad.getSLiteral());
                case S_TRIPLE_TERM -> from(quad.getSTripleTerm());
                case SUBJECT_NOT_SET -> null;
            };

        final var predicate =
            switch (quad.getPredicateCase()) {
                case P_IRI -> from(quad.getPIri());
                case P_BNODE -> from(quad.getPBnode());
                case P_LITERAL -> from(quad.getPLiteral());
                case P_TRIPLE_TERM -> from(quad.getPTripleTerm());
                case PREDICATE_NOT_SET -> null;
            };

        final var object =
            switch (quad.getObjectCase()) {
                case O_IRI -> from(quad.getOIri());
                case O_BNODE -> from(quad.getOBnode());
                case O_LITERAL -> from(quad.getOLiteral());
                case O_TRIPLE_TERM -> from(quad.getOTripleTerm());
                case OBJECT_NOT_SET -> null;
            };

        final var graph =
            switch (quad.getGraphCase()) {
                case G_IRI -> from(quad.getGIri());
                case G_BNODE -> from(quad.getGBnode());
                case G_DEFAULT_GRAPH -> from(quad.getGDefaultGraph());
                case G_LITERAL -> from(quad.getGLiteral());
                case GRAPH_NOT_SET -> null;
            };

        return new Quad(subject, predicate, object, graph);
    }

    /**
     * Represents terms that can appear in subject, predicate, or object positions of a triple.
     */
    sealed interface SpoTerm extends RdfTerm {
        /**
         * Converts the term to a Protocol Buffer RDF triple subject term.
         */
        void writeSubject(RdfTriple.Builder builder);

        /**
         * Converts the term to a Protocol Buffer RDF quad subject term.
         */
        void writeSubject(RdfQuad.Builder builder);

        /**
         * Converts the term to a Protocol Buffer RDF triple predicate term.
         */
        void writePredicate(RdfTriple.Builder builder);

        /**
         * Converts the term to a Protocol Buffer RDF quad predicate term.
         */
        void writePredicate(RdfQuad.Builder builder);

        /**
         * Converts the term to a Protocol Buffer RDF triple object term.
         */
        void writeObject(RdfTriple.Builder builder);

        /**
         * Converts the term to a Protocol Buffer RDF quad object term.
         */
        void writeObject(RdfQuad.Builder builder);
    }

    /**
     * Represents terms that mark graph boundaries in the RDF dataset.
     */
    sealed interface GraphMarkerTerm extends RdfTerm {}

    /**
     * Represents terms that can appear as graph labels.
     */
    sealed interface GraphTerm extends RdfTerm {
        /**
         * Converts the term to a Protocol Buffer RDF graph start message.
         */
        void writeGraph(RdfGraphStart.Builder builder);

        /**
         * Converts the term to a Protocol Buffer RDF quad graph message.
         */
        void writeGraph(RdfQuad.Builder builder);
    }

    /**
     * Represents terms that can appear in SPO positions and as graph labels.
     */
    sealed interface SpoOrGraphTerm extends SpoTerm, GraphTerm {}

    /**
     * Represents literal terms with lexical values.
     */
    sealed interface LiteralTerm extends SpoOrGraphTerm {
        String lex();

        RdfLiteral toProto();
    }

    /**
     * Represents terms that can be either graph markers or graph labels.
     */
    sealed interface GraphMarkerOrGraphTerm extends GraphMarkerTerm, GraphTerm {}

    /**
     * Represents IRI terms with prefix and name identifiers.
     *
     * @param prefixId The prefix identifier
     * @param nameId The name identifier
     */
    record Iri(int prefixId, int nameId) implements SpoOrGraphTerm {
        public RdfIri toProto() {
            return RdfIri.newBuilder().setPrefixId(prefixId).setNameId(nameId).build();
        }

        @Override
        public void writeSubject(RdfTriple.Builder builder) {
            builder.setSIri(toProto());
        }

        @Override
        public void writeSubject(RdfQuad.Builder builder) {
            builder.setSIri(toProto());
        }

        @Override
        public void writePredicate(RdfTriple.Builder builder) {
            builder.setPIri(toProto());
        }

        @Override
        public void writePredicate(RdfQuad.Builder builder) {
            builder.setPIri(toProto());
        }

        @Override
        public void writeObject(RdfTriple.Builder builder) {
            builder.setOIri(toProto());
        }

        @Override
        public void writeObject(RdfQuad.Builder builder) {
            builder.setOIri(toProto());
        }

        @Override
        public void writeGraph(RdfGraphStart.Builder builder) {
            builder.setGIri(toProto());
        }

        @Override
        public void writeGraph(RdfQuad.Builder builder) {
            builder.setGIri(toProto());
        }
    }

    /**
     * Represents blank node terms with a string identifier.
     *
     * @param bNode The blank node identifier
     */
    record BNode(String bNode) implements SpoOrGraphTerm {
        public String toProto() {
            return bNode;
        }

        @Override
        public void writeSubject(RdfTriple.Builder builder) {
            builder.setSBnode(toProto());
        }

        @Override
        public void writeSubject(RdfQuad.Builder builder) {
            builder.setSBnode(toProto());
        }

        @Override
        public void writePredicate(RdfTriple.Builder builder) {
            builder.setPBnode(toProto());
        }

        @Override
        public void writePredicate(RdfQuad.Builder builder) {
            builder.setPBnode(toProto());
        }

        @Override
        public void writeObject(RdfTriple.Builder builder) {
            builder.setOBnode(toProto());
        }

        @Override
        public void writeObject(RdfQuad.Builder builder) {
            builder.setOBnode(toProto());
        }

        @Override
        public void writeGraph(RdfGraphStart.Builder builder) {
            builder.setGBnode(toProto());
        }

        @Override
        public void writeGraph(RdfQuad.Builder builder) {
            builder.setGBnode(toProto());
        }
    }

    /**
     * Represents literal terms with lexical values and language tags.
     *
     * @param lex The lexical value
     * @param langtag The language tag
     */
    record LanguageLiteral(String lex, String langtag) implements LiteralTerm {
        public RdfLiteral toProto() {
            return RdfLiteral.newBuilder().setLex(lex).setLangtag(langtag).build();
        }

        @Override
        public void writeSubject(RdfTriple.Builder builder) {
            builder.setSLiteral(toProto());
        }

        @Override
        public void writeSubject(RdfQuad.Builder builder) {
            builder.setSLiteral(toProto());
        }

        @Override
        public void writePredicate(RdfTriple.Builder builder) {
            builder.setPLiteral(toProto());
        }

        @Override
        public void writePredicate(RdfQuad.Builder builder) {
            builder.setPLiteral(toProto());
        }

        @Override
        public void writeObject(RdfTriple.Builder builder) {
            builder.setOLiteral(toProto());
        }

        @Override
        public void writeObject(RdfQuad.Builder builder) {
            builder.setOLiteral(toProto());
        }

        @Override
        public void writeGraph(RdfGraphStart.Builder builder) {
            builder.setGLiteral(toProto());
        }

        @Override
        public void writeGraph(RdfQuad.Builder builder) {
            builder.setGLiteral(toProto());
        }
    }

    /**
     * Represents literal terms with lexical values and datatype identifiers.
     *
     * @param lex The lexical value
     * @param datatype The datatype identifier
     */
    record DtLiteral(String lex, int datatype) implements LiteralTerm {
        public RdfLiteral toProto() {
            return RdfLiteral.newBuilder().setLex(lex).setDatatype(datatype).build();
        }

        @Override
        public void writeSubject(RdfTriple.Builder builder) {
            builder.setSLiteral(toProto());
        }

        @Override
        public void writeSubject(RdfQuad.Builder builder) {
            builder.setSLiteral(toProto());
        }

        @Override
        public void writePredicate(RdfTriple.Builder builder) {
            builder.setPLiteral(toProto());
        }

        @Override
        public void writePredicate(RdfQuad.Builder builder) {
            builder.setPLiteral(toProto());
        }

        @Override
        public void writeObject(RdfTriple.Builder builder) {
            builder.setOLiteral(toProto());
        }

        @Override
        public void writeObject(RdfQuad.Builder builder) {
            builder.setOLiteral(toProto());
        }

        @Override
        public void writeGraph(RdfGraphStart.Builder builder) {
            builder.setGLiteral(toProto());
        }

        @Override
        public void writeGraph(RdfQuad.Builder builder) {
            builder.setGLiteral(toProto());
        }
    }

    /**
     * Represents simple literal terms with lexical values.
     *
     * @param lex The lexical value
     */
    record SimpleLiteral(String lex) implements LiteralTerm {
        public RdfLiteral toProto() {
            return RdfLiteral.newBuilder().setLex(lex).build();
        }

        @Override
        public void writeSubject(RdfTriple.Builder builder) {
            builder.setSLiteral(toProto());
        }

        @Override
        public void writeSubject(RdfQuad.Builder builder) {
            builder.setSLiteral(toProto());
        }

        @Override
        public void writePredicate(RdfTriple.Builder builder) {
            builder.setPLiteral(toProto());
        }

        @Override
        public void writePredicate(RdfQuad.Builder builder) {
            builder.setPLiteral(toProto());
        }

        @Override
        public void writeObject(RdfTriple.Builder builder) {
            builder.setOLiteral(toProto());
        }

        @Override
        public void writeObject(RdfQuad.Builder builder) {
            builder.setOLiteral(toProto());
        }

        @Override
        public void writeGraph(RdfGraphStart.Builder builder) {
            builder.setGLiteral(toProto());
        }

        @Override
        public void writeGraph(RdfQuad.Builder builder) {
            builder.setGLiteral(toProto());
        }
    }

    /**
     * Represents RDF triples with subject, predicate, and object terms.
     *
     * @param subject The subject term
     * @param predicate The predicate term
     * @param object The object term
     */
    record Triple(SpoTerm subject, SpoTerm predicate, SpoTerm object) implements SpoTerm {
        public RdfTriple toProto() {
            final var tripleBuilder = RdfTriple.newBuilder();

            if (subject != null) {
                subject.writeSubject(tripleBuilder);
            }

            if (predicate != null) {
                predicate.writePredicate(tripleBuilder);
            }

            if (object != null) {
                object.writeObject(tripleBuilder);
            }

            return tripleBuilder.build();
        }

        @Override
        public void writeSubject(RdfTriple.Builder builder) {
            builder.setSTripleTerm(toProto());
        }

        @Override
        public void writeSubject(RdfQuad.Builder builder) {
            builder.setSTripleTerm(toProto());
        }

        @Override
        public void writePredicate(RdfTriple.Builder builder) {
            builder.setPTripleTerm(toProto());
        }

        @Override
        public void writePredicate(RdfQuad.Builder builder) {
            builder.setPTripleTerm(toProto());
        }

        @Override
        public void writeObject(RdfTriple.Builder builder) {
            builder.setOTripleTerm(toProto());
        }

        @Override
        public void writeObject(RdfQuad.Builder builder) {
            builder.setOTripleTerm(toProto());
        }
    }

    /**
     * Represents graph start markers with optional graph labels.
     *
     * @param graph The graph label term
     */
    record GraphStart(GraphTerm graph) implements GraphMarkerTerm {
        public RdfGraphStart toProto() {
            final var graphBuilder = RdfGraphStart.newBuilder();

            if (graph != null) {
                graph.writeGraph(graphBuilder);
            }

            return graphBuilder.build();
        }
    }

    /**
     * Represents graph end markers.
     */
    record GraphEnd() implements GraphMarkerTerm {
        public RdfGraphEnd toProto() {
            return RdfGraphEnd.getDefaultInstance();
        }
    }

    /**
     * Represents default graph markers.
     */
    record DefaultGraph() implements GraphMarkerOrGraphTerm {
        public static final DefaultGraph INSTANCE = new DefaultGraph();

        public RdfDefaultGraph toProto() {
            return RdfDefaultGraph.getDefaultInstance();
        }

        @Override
        public void writeGraph(RdfGraphStart.Builder builder) {
            builder.setGDefaultGraph(toProto());
        }

        @Override
        public void writeGraph(RdfQuad.Builder builder) {
            builder.setGDefaultGraph(toProto());
        }
    }

    /**
     * Represents RDF quads with subject, predicate, object, and graph terms.
     *
     * @param subject The subject term
     * @param predicate The predicate term
     * @param object The object term
     * @param graph The graph term
     */
    record Quad(SpoTerm subject, SpoTerm predicate, SpoTerm object, GraphTerm graph) implements RdfTerm {
        public RdfQuad toProto() {
            final var quadBuilder = RdfQuad.newBuilder();

            if (subject != null) {
                subject.writeSubject(quadBuilder);
            }

            if (predicate != null) {
                predicate.writePredicate(quadBuilder);
            }

            if (object != null) {
                object.writeObject(quadBuilder);
            }

            if (graph != null) {
                graph.writeGraph(quadBuilder);
            }

            return quadBuilder.build();
        }
    }
}
