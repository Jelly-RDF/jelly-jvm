package eu.ostrzyciel.jelly.core;

public sealed interface RdfTerm {

    sealed interface SpoTerm extends RdfTerm {
    }

    sealed interface GraphMarkerTerm extends RdfTerm {
    }

    sealed interface GraphTerm extends RdfTerm {
    }

    sealed interface SpoOrGraphTerm extends SpoTerm, GraphTerm {
    }

    sealed interface GraphMarkerOrGraphTerm extends GraphMarkerTerm, GraphTerm {
    }

    record Iri(int prefixId, int nameId) implements SpoOrGraphTerm {
    }

    record BNode(String bNode) implements SpoOrGraphTerm {
    }

    record LanguageLiteral(String lex, String langtag) implements SpoOrGraphTerm {
    }

    record DtLiteral(String lex, int datatype) implements SpoOrGraphTerm {
    }

    record SimpleLiteral(String lex) implements SpoOrGraphTerm {
    }

    record Triple(SpoTerm subject, SpoTerm predicate, SpoTerm object) implements SpoTerm {
    }

    record GraphStart(GraphTerm graph) implements GraphMarkerTerm {
    }

    record GraphEnd() implements GraphMarkerTerm {
    }

    record DefaultGraph() implements GraphMarkerOrGraphTerm {
    }

    record Quad(SpoTerm subject, SpoTerm predicate, SpoTerm object, GraphTerm graph) implements RdfTerm {
    }
}
