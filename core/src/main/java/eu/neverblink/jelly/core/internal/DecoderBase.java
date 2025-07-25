package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.NameDecoder;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.internal.proto.GraphBase;
import eu.neverblink.jelly.core.internal.proto.SpoBase;
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
     * @param graph graph term to convert
     * @return converted node
     * @throws RdfProtoDeserializationError if the graph term can't be decoded
     */
    protected final TNode convertGraphTerm(Object graph) {
        if (graph == null) {
            throw new RdfProtoDeserializationError("Empty graph term encountered in a GRAPHS stream.");
        }

        try {
            if (graph instanceof RdfIri iri) {
                return getNameDecoder().decode(iri.getPrefixId(), iri.getNameId());
            } else if (graph instanceof String bnode) {
                return converter.makeBlankNode(bnode);
            } else if (graph instanceof RdfDefaultGraph) {
                return converter.makeDefaultGraphNode();
            } else if (graph instanceof RdfLiteral literal) {
                return convertLiteral(literal);
            } else {
                throw new RdfProtoDeserializationError(
                    "Unknown graph term type: %s".formatted(graph.getClass().getName())
                );
            }
        } catch (Exception e) {
            throw new RdfProtoDeserializationError("Error while decoding graph term %s".formatted(e), e);
        }
    }

    /**
     * Convert a SpoTerm message to a node.
     * @param term term to convert
     * @throws RdfProtoDeserializationError if the term can't be decoded
     */
    protected final TNode convertTerm(Object term) {
        if (term == null) {
            throw new RdfProtoDeserializationError("Term value is not set inside a quoted triple.");
        }
        try {
            if (term instanceof RdfIri iri) {
                return getNameDecoder().decode(iri.getPrefixId(), iri.getNameId());
            } else if (term instanceof String bNode) {
                return converter.makeBlankNode(bNode);
            } else if (term instanceof RdfLiteral literal) {
                return convertLiteral(literal);
            } else if (term instanceof RdfTriple triple) {
                return converter.makeTripleNode(
                    convertTerm(triple.getSubject()),
                    convertTerm(triple.getPredicate()),
                    convertTerm(triple.getObject())
                );
            } else {
                throw new RdfProtoDeserializationError("Unknown term type: %s".formatted(term.getClass().getName()));
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
     * The logic here is repeated in the other SPO methods for performance reasons (avoiding additional reference passing).
     * @param spo SPO-like message to extract the subject from
     * @return converted node
     */
    protected final TNode convertSubjectTermWrapped(SpoBase spo) {
        final var term = spo.getSubject();
        if (term == null && lastSubject == null) {
            throw new RdfProtoDeserializationError("Empty subject term without previous term.");
        }

        if (term == null) {
            return lastSubject;
        }

        final var node = convertTerm(term);
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
        final var term = spo.getPredicate();
        if (term == null && lastPredicate == null) {
            throw new RdfProtoDeserializationError("Empty subject term without previous term.");
        }

        if (term == null) {
            return lastPredicate;
        }

        final var node = convertTerm(term);
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
        final var term = spo.getObject();
        if (term == null && lastObject == null) {
            throw new RdfProtoDeserializationError("Empty subject term without previous term.");
        }

        if (term == null) {
            return lastObject;
        }

        final var node = convertTerm(term);
        lastObject = node;
        return node;
    }

    /**
     * Convert a GraphTerm message to a node, while respecting repeated terms.
     * @param graph GraphBase message to convert
     * @return converted node
     */
    protected final TNode convertGraphTermWrapped(GraphBase graph) {
        if (graph.getGraph() == null && lastGraph == null) {
            // Special case: Jena and RDF4J allow null graph terms in the input,
            // so we do not treat them as errors.
            return null;
        }

        if (graph.getGraph() == null) {
            return lastGraph;
        }

        final var node = convertGraphTerm(graph.getGraph());
        lastGraph = node;
        return node;
    }
}
