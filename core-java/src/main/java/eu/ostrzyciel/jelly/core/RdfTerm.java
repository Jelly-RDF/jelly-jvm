package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.RdfDefaultGraph;
import eu.ostrzyciel.jelly.core.proto.v1.RdfGraphEnd;
import eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart;
import eu.ostrzyciel.jelly.core.proto.v1.RdfIri;
import eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral;
import eu.ostrzyciel.jelly.core.proto.v1.RdfQuad;
import eu.ostrzyciel.jelly.core.proto.v1.RdfTriple;

public sealed interface RdfTerm {
    static Iri from(RdfIri iri) {
        return new Iri(iri.getPrefixId(), iri.getNameId());
    }

    static BNode from(String bNode) {
        return new BNode(bNode);
    }

    static LiteralTerm from(RdfLiteral literal) {
        if (literal.hasLangtag()) {
            return new LanguageLiteral(literal.getLex(), literal.getLangtag());
        } else if (literal.hasDatatype()) {
            return new DtLiteral(literal.getLex(), literal.getDatatype());
        } else {
            return new SimpleLiteral(literal.getLex());
        }
    }

    static Triple from(RdfTriple triple) {
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

    static GraphStart from(RdfGraphStart graphStart) {
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

    static GraphEnd from(RdfGraphEnd ignoredGraphEnd) {
        return new GraphEnd();
    }

    static DefaultGraph from(RdfDefaultGraph ignoredDefaultGraph) {
        return new DefaultGraph();
    }

    static Quad from(RdfQuad quad) {
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

    sealed interface SpoTerm extends RdfTerm {
        void writeSubject(RdfTriple.Builder builder);

        void writeSubject(RdfQuad.Builder builder);

        void writePredicate(RdfTriple.Builder builder);

        void writePredicate(RdfQuad.Builder builder);

        void writeObject(RdfTriple.Builder builder);

        void writeObject(RdfQuad.Builder builder);
    }

    sealed interface GraphMarkerTerm extends RdfTerm {}

    sealed interface GraphTerm extends RdfTerm {
        void writeGraph(RdfGraphStart.Builder builder);

        void writeGraph(RdfQuad.Builder builder);
    }

    sealed interface SpoOrGraphTerm extends SpoTerm, GraphTerm {}

    sealed interface LiteralTerm extends SpoOrGraphTerm {
        String lex();
    }

    sealed interface GraphMarkerOrGraphTerm extends GraphMarkerTerm, GraphTerm {}

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

    record Triple(SpoTerm subject, SpoTerm predicate, SpoTerm object) implements SpoTerm {
        public RdfTriple toProto() {
            final var tripleBuilder = RdfTriple.newBuilder();

            subject.writeSubject(tripleBuilder);
            predicate.writePredicate(tripleBuilder);
            object.writeObject(tripleBuilder);

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

    record GraphStart(GraphTerm graph) implements GraphMarkerTerm {
        public RdfGraphStart toProto() {
            final var graphBuilder = RdfGraphStart.newBuilder();
            graph.writeGraph(graphBuilder);
            return graphBuilder.build();
        }
    }

    record GraphEnd() implements GraphMarkerTerm {
        public RdfGraphEnd toProto() {
            return RdfGraphEnd.getDefaultInstance();
        }
    }

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

    record Quad(SpoTerm subject, SpoTerm predicate, SpoTerm object, GraphTerm graph) implements RdfTerm {
        public RdfQuad toProto() {
            final var quadBuilder = RdfQuad.newBuilder();

            subject.writeSubject(quadBuilder);
            predicate.writePredicate(quadBuilder);
            object.writeObject(quadBuilder);
            graph.writeGraph(quadBuilder);

            return quadBuilder.build();
        }
    }
}
