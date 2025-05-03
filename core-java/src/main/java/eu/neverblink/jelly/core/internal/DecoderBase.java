package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.internal.utils.LazyProperty;

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
     * @param graph graph term to convert
     * @return converted node
     * @throws RdfProtoDeserializationError if the graph term can't be decoded
     */
    protected final TNode convertGraphTerm(RdfTerm.GraphTerm graph) {
        try {
            if (graph == null) {
                throw new RdfProtoDeserializationError("Empty graph term encountered in a GRAPHS stream.");
            } else if (graph instanceof RdfTerm.Iri iri) {
                return nameDecoder.provide().decode(iri.prefixId(), iri.nameId());
            } else if (graph instanceof RdfTerm.DefaultGraph) {
                return converter.makeDefaultGraphNode();
            } else if (graph instanceof RdfTerm.BNode bnode) {
                return converter.makeBlankNode(bnode.bNode());
            } else if (graph instanceof RdfTerm.LanguageLiteral languageLiteral) {
                return converter.makeLangLiteral(languageLiteral.lex(), languageLiteral.langtag());
            } else if (graph instanceof RdfTerm.DtLiteral dtLiteral) {
                return converter.makeDtLiteral(dtLiteral.lex(), datatypeLookup.provide().get(dtLiteral.datatype()));
            } else if (graph instanceof RdfTerm.SimpleLiteral simpleLiteral) {
                return converter.makeSimpleLiteral(simpleLiteral.lex());
            } else {
                throw new RdfProtoDeserializationError("Unknown graph term type.");
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
    protected final TNode convertTerm(RdfTerm.SpoTerm term) {
        try {
            if (term == null) {
                throw new RdfProtoDeserializationError("Term value is not set inside a quoted triple.");
            } else if (term instanceof RdfTerm.Iri iri) {
                return nameDecoder.provide().decode(iri.prefixId(), iri.nameId());
            } else if (term instanceof RdfTerm.BNode bnode) {
                return converter.makeBlankNode(bnode.bNode());
            } else if (term instanceof RdfTerm.LanguageLiteral languageLiteral) {
                return converter.makeLangLiteral(languageLiteral.lex(), languageLiteral.langtag());
            } else if (term instanceof RdfTerm.DtLiteral dtLiteral) {
                return converter.makeDtLiteral(dtLiteral.lex(), datatypeLookup.provide().get(dtLiteral.datatype()));
            } else if (term instanceof RdfTerm.SimpleLiteral simpleLiteral) {
                return converter.makeSimpleLiteral(simpleLiteral.lex());
            } else if (term instanceof RdfTerm.Triple triple) {
                // ! No support for repeated terms in quoted triples
                return converter.makeTripleNode(
                    convertTerm(triple.subject()),
                    convertTerm(triple.predicate()),
                    convertTerm(triple.object())
                );
            } else {
                throw new RdfProtoDeserializationError("Unknown term type.");
            }
        } catch (Exception e) {
            throw new RdfProtoDeserializationError("Error while decoding term %s".formatted(e), e);
        }
    }

    /**
     * Convert a subject SpoTerm message to a node, while respecting repeated terms.
     * @param subject term to convert
     * @return converted node
     */
    protected final TNode convertSubjectTermWrapped(RdfTerm.SpoTerm subject) {
        return convertSpoTermWrapped(subject, lastSubject);
    }

    /**
     * Convert a predicate SpoTerm message to a node, while respecting repeated terms.
     * @param predicate term to convert
     * @return converted node
     */
    protected final TNode convertPredicateTermWrapped(RdfTerm.SpoTerm predicate) {
        return convertSpoTermWrapped(predicate, lastPredicate);
    }

    /**
     * Convert an object SpoTerm message to a node, while respecting repeated terms.
     * @param object term to convert
     * @return converted node
     */
    protected final TNode convertObjectTermWrapped(RdfTerm.SpoTerm object) {
        return convertSpoTermWrapped(object, lastObject);
    }

    /**
     * Convert a GraphTerm message to a node, while respecting repeated terms.
     * @param graph graph term to convert
     * @return converted node
     */
    protected final TNode convertGraphTermWrapped(RdfTerm.GraphTerm graph) {
        if (graph == null && lastGraph.hasNoValue()) {
            // Special case: Jena and RDF4J allow null graph terms in the input, so we do not treat them as errors.
            return null;
        }

        if (graph == null) {
            return lastGraph.get();
        }

        final var node = convertGraphTerm(graph);
        lastGraph.set(node);
        return node;
    }

    private TNode convertSpoTermWrapped(RdfTerm.SpoTerm term, LastNodeHolder<TNode> lastNodeHolder) {
        if (term == null && lastNodeHolder.hasNoValue()) {
            throw new RdfProtoDeserializationError("Empty term without previous term.");
        }

        if (term == null) {
            return lastNodeHolder.get();
        }

        final var node = convertTerm(term);
        lastNodeHolder.set(node);
        return node;
    }
}
