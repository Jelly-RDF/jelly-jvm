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
    protected final LazyProperty<NameDecoder<TNode>> nameDecoder;
    protected final LazyProperty<DecoderLookup<TDatatype>> datatypeLookup;

    protected final LastNodeHolder<TNode> lastSubject = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastPredicate = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastObject = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastGraph = new LastNodeHolder<>();

    protected DecoderBase(ProtoDecoderConverter<TNode, TDatatype> converter) {
        this.converter = converter;
        this.datatypeLookup = new LazyProperty<>(() -> new DecoderLookup<>(getDatatypeTableSize()));
        this.nameDecoder = new LazyProperty<>(() ->
            new NameDecoderImpl<>(getPrefixTableSize(), getNameTableSize(), converter::makeIriNode)
        );
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
                    return nameDecoder.provide().decode(iri.getPrefixId(), iri.getNameId());
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
                    return nameDecoder.provide().decode(iri.getPrefixId(), iri.getNameId());
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
                return converter.makeDtLiteral(literal.getLex(), datatypeLookup.provide().get(literal.getDatatype()));
            }
            default -> {
                return converter.makeSimpleLiteral(literal.getLex());
            }
        }
    }

    /**
     * Convert the subject from an SPO-like message to a node, while respecting repeated terms.
     * @param spo SPO-like message to extract the subject from
     * @return converted node
     */
    protected final TNode convertSubjectTermWrapped(SpoBase spo) {
        return convertSpoTermWrapped(spo.getSubjectFieldNumber() - RdfTriple.S_IRI, spo.getSubject(), lastSubject);
    }

    /**
     * Convert the predicate from an SPO-like message to a node, while respecting repeated terms.
     * @param spo SPO-like message to extract the predicate from
     * @return converted node
     */
    protected final TNode convertPredicateTermWrapped(SpoBase spo) {
        return convertSpoTermWrapped(
            spo.getPredicateFieldNumber() - RdfTriple.P_IRI,
            spo.getPredicate(),
            lastPredicate
        );
    }

    /**
     * Convert the object from an SPO-like message to a node, while respecting repeated terms.
     * @param spo SPO-like message to extract the object from
     * @return converted node
     */
    protected final TNode convertObjectTermWrapped(SpoBase spo) {
        return convertSpoTermWrapped(spo.getObjectFieldNumber() - RdfTriple.O_IRI, spo.getObject(), lastObject);
    }

    /**
     * Convert a GraphTerm message to a node, while respecting repeated terms.
     * @param kind field number of the term, normalized to 0, 1, 2, 3
     * @param graph GraphBase message to convert
     * @return converted node
     */
    protected final TNode convertGraphTermWrapped(int kind, GraphBase graph) {
        if (kind < 0 && lastGraph.hasNoValue()) {
            // Special case: Jena and RDF4J allow null graph terms in the input,
            // so we do not treat them as errors.
            return null;
        }

        // Checking for null here would not be as reliable, because if we call clear() on the quad,
        // only the row number is set to 0, but the value is not set to null.
        // This makes a difference if we are reusing the same object for multiple triples.
        if (kind < 0) {
            return lastGraph.get();
        }

        final var node = convertGraphTerm(kind, graph.getGraph());
        lastGraph.set(node);
        return node;
    }

    private TNode convertSpoTermWrapped(int kind, Object term, LastNodeHolder<TNode> lastNodeHolder) {
        if (kind < 0 && lastNodeHolder.hasNoValue()) {
            throw new RdfProtoDeserializationError("Empty term without previous term.");
        }

        if (kind < 0) {
            return lastNodeHolder.get();
        }

        final var node = convertTerm(kind, term);
        lastNodeHolder.set(node);
        return node;
    }
}
