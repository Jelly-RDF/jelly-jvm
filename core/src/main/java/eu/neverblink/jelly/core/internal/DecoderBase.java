package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.NameDecoder;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.internal.proto.GraphBase;
import eu.neverblink.jelly.core.internal.proto.SpoBase;
import eu.neverblink.jelly.core.internal.utils.LazyProperty;
import eu.neverblink.jelly.core.proto.v1.*;

/**
 * Base trait for Jelly proto decoders. Only for internal use.
 * @param <TNode> type of RDF nodes in the library
 * @param <TDatatype> type of the datatype in the library
 */
@InternalApi
public abstract class DecoderBase<TNode, TDatatype> {

    protected final ProtoDecoderConverter<TNode, TDatatype> converter;
    private NameDecoder<TNode> nameDecoder = null;
    private DecoderLookup<TDatatype> datatypeLookup = null;

    protected TNode lastSubject = null;
    protected TNode lastPredicate = null;
    protected TNode lastObject = null;
    protected TNode lastGraph = null;

    protected DecoderBase(ProtoDecoderConverter<TNode, TDatatype> converter) {
        this.converter = converter;
    }

    protected final NameDecoder<TNode> getNameDecoder() {
        if (nameDecoder == null) {
            nameDecoder = new NameDecoderImpl<>(getPrefixTableSize(), getNameTableSize(), converter::makeIriNode);
        }
        return nameDecoder;
    }

    protected final DecoderLookup<TDatatype> getDatatypeLookup() {
        if (datatypeLookup == null) {
            datatypeLookup = new DecoderLookup<>(getDatatypeTableSize());
        }
        return datatypeLookup;
    }

    protected abstract int getNameTableSize();

    protected abstract int getPrefixTableSize();

    protected abstract int getDatatypeTableSize();

    /**
     * Convert a GraphTerm message to a node.
     * @param kind field number of the term, normalized to 0, 1, 2, 3
     * @param graph graph term to convert
     * @return converted node
     * @throws RdfProtoDeserializationError if the graph term can't be decoded
     */
    protected final TNode convertGraphTerm(int kind, Object graph) {
        if (graph == null) {
            throw new RdfProtoDeserializationError("Empty graph term encountered in a GRAPHS stream.");
        }

        try {
            switch (kind) {
                case 0 -> {
                    final var iri = (RdfIri) graph;
                    return getNameDecoder().decode(iri.getPrefixId(), iri.getNameId());
                }
                case 1 -> {
                    final var bnode = (String) graph;
                    return converter.makeBlankNode(bnode);
                }
                case 2 -> {
                    return converter.makeDefaultGraphNode();
                }
                case 3 -> {
                    return convertLiteral((RdfLiteral) graph);
                }
                default -> throw new RdfProtoDeserializationError("Unknown graph term type");
            }
        } catch (Exception e) {
            throw new RdfProtoDeserializationError("Error while decoding graph term %s".formatted(e), e);
        }
    }

    /**
     * Convert a SpoTerm message to a node.
     * @param kind field number of the term, normalized to 0, 1, 2, 3
     * @param term term to convert
     * @throws RdfProtoDeserializationError if the term can't be decoded
     */
    protected final TNode convertTerm(int kind, Object term) {
        if (term == null) {
            throw new RdfProtoDeserializationError("Term value is not set inside a quoted triple.");
        }
        try {
            switch (kind) {
                case 0 -> {
                    final var iri = (RdfIri) term;
                    return getNameDecoder().decode(iri.getPrefixId(), iri.getNameId());
                }
                case 1 -> {
                    final var bnode = (String) term;
                    return converter.makeBlankNode(bnode);
                }
                case 2 -> {
                    return convertLiteral((RdfLiteral) term);
                }
                case 3 -> {
                    final var triple = (RdfTriple) term;
                    return converter.makeTripleNode(
                        convertTerm(triple.getSubjectFieldNumber() - RdfTriple.S_IRI, triple.getSubject()),
                        convertTerm(triple.getPredicateFieldNumber() - RdfTriple.P_IRI, triple.getPredicate()),
                        convertTerm(triple.getObjectFieldNumber() - RdfTriple.O_IRI, triple.getObject())
                    );
                }
                default -> throw new RdfProtoDeserializationError("Unknown term type");
            }
        } catch (Exception e) {
            throw new RdfProtoDeserializationError("Error while decoding term %s".formatted(e), e);
        }
    }

    private TNode convertLiteral(RdfLiteral literal) {
        switch (literal.getLiteralKindFieldNumber()) {
            case RdfLiteral.LANGTAG -> {
                return converter.makeLangLiteral(literal.getLex(), literal.getLangtag());
            }
            case RdfLiteral.DATATYPE -> {
                return converter.makeDtLiteral(literal.getLex(), getDatatypeLookup().get(literal.getDatatype()));
            }
            default -> {
                return converter.makeSimpleLiteral(literal.getLex());
            }
        }
    }

    /**
     * Convert the subject from an SPO-like message to a node, while respecting repeated terms.
     * <p>
     * The logic here is repeated in the other SPO methods for permance reasons (avoiding additional reference passing).
     * @param spo SPO-like message to extract the subject from
     * @return converted node
     */
    protected final TNode convertSubjectTermWrapped(SpoBase spo) {
        int kind = spo.getSubjectFieldNumber() - RdfTriple.S_IRI;
        final var term = spo.getSubject();
        if (term == null && lastSubject == null) {
            throw new RdfProtoDeserializationError("Empty subject term without previous term.");
        }

        if (term == null) {
            return lastSubject;
        }

        final var node = convertTerm(kind, term);
        lastSubject = node;
        return node;
    }

    /**
     * Convert the predicate from an SPO-like message to a node, while respecting repeated terms.
     * <p>
     * The logic here is repeated in the other SPO methods for permance reasons (avoiding additional reference passing).
     * @param spo SPO-like message to extract the predicate from
     * @return converted node
     */
    protected final TNode convertPredicateTermWrapped(SpoBase spo) {
        int kind = spo.getPredicateFieldNumber() - RdfTriple.P_IRI;
        final var term = spo.getPredicate();
        if (term == null && lastPredicate == null) {
            throw new RdfProtoDeserializationError("Empty subject term without previous term.");
        }

        if (term == null) {
            return lastPredicate;
        }

        final var node = convertTerm(kind, term);
        lastPredicate = node;
        return node;
    }

    /**
     * Convert the object from an SPO-like message to a node, while respecting repeated terms.
     * <p>
     * The logic here is repeated in the other SPO methods for permance reasons (avoiding additional reference passing).
     * @param spo SPO-like message to extract the object from
     * @return converted node
     */
    protected final TNode convertObjectTermWrapped(SpoBase spo) {
        int kind = spo.getObjectFieldNumber() - RdfTriple.O_IRI;
        final var term = spo.getObject();
        if (term == null && lastObject == null) {
            throw new RdfProtoDeserializationError("Empty subject term without previous term.");
        }

        if (term == null) {
            return lastObject;
        }

        final var node = convertTerm(kind, term);
        lastObject = node;
        return node;
    }

    /**
     * Convert a GraphTerm message to a node, while respecting repeated terms.
     * @param kind field number of the term, normalized to 0, 1, 2, 3
     * @param graph GraphBase message to convert
     * @return converted node
     */
    protected final TNode convertGraphTermWrapped(int kind, GraphBase graph) {
        if (graph.getGraph() == null && lastGraph == null) {
            // Special case: Jena and RDF4J allow null graph terms in the input,
            // so we do not treat them as errors.
            return null;
        }

        if (graph.getGraph() == null) {
            return lastGraph;
        }

        final var node = convertGraphTerm(kind, graph.getGraph());
        lastGraph = node;
        return node;
    }
}
