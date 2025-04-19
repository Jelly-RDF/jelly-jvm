package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.ProtoDecoderConverter;
import eu.ostrzyciel.jelly.core.RdfProtoDeserializationError;
import eu.ostrzyciel.jelly.core.RdfTerm;

public abstract class ProtoDecoderBase<TNode, TDatatype> {

    protected final ProtoDecoderConverter<TNode, TDatatype> converter;
    protected final NameDecoder<TNode> nameDecoder;
    protected final DecoderLookup<TDatatype> datatypeLookup;

    protected final LastNodeHolder<TNode> lastSubject = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastPredicate = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastObject = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastGraph = new LastNodeHolder<>();

    protected ProtoDecoderBase(ProtoDecoderConverter<TNode, TDatatype> converter, NameDecoder<TNode> nameDecoder) {
        this.converter = converter;
        this.nameDecoder = nameDecoder;
        this.datatypeLookup = new DecoderLookup<>(getDatatypeTableSize());
    }

    protected abstract int getNameTableSize();

    protected abstract int getPrefixTableSize();

    protected abstract int getDatatypeTableSize();

    protected final TNode convertGraphTerm(RdfTerm.GraphTerm graph) {
        if (graph == null) {
            throw new RdfProtoDeserializationError("Empty graph term encountered in a GRAPHS stream.");
        } else if (graph instanceof RdfTerm.Iri iri) {
            return nameDecoder.decode(iri.nameId(), iri.prefixId());
        } else if (graph instanceof RdfTerm.DefaultGraph) {
            return converter.makeDefaultGraphNode();
        } else if (graph instanceof RdfTerm.BNode bnode) {
            return converter.makeBlankNode(bnode.bNode());
        } else if (graph instanceof RdfTerm.LanguageLiteral languageLiteral) {
            return converter.makeLangLiteral(languageLiteral.lex(), languageLiteral.langtag());
        } else if (graph instanceof RdfTerm.DtLiteral dtLiteral) {
            return converter.makeDtLiteral(dtLiteral.lex(), datatypeLookup.get(dtLiteral.datatype()));
        } else if (graph instanceof RdfTerm.SimpleLiteral simpleLiteral) {
            return converter.makeSimpleLiteral(simpleLiteral.lex());
        } else {
            throw new RdfProtoDeserializationError("Unknown graph term type.");
        }
    }

    protected final TNode convertTerm(RdfTerm.SpoTerm term) {
        if (term == null) {
            throw new RdfProtoDeserializationError("Term value is not set inside a quoted triple.");
        } else if (term instanceof RdfTerm.Iri iri) {
            return nameDecoder.decode(iri.nameId(), iri.prefixId());
        } else if (term instanceof RdfTerm.BNode bnode) {
            return converter.makeBlankNode(bnode.bNode());
        } else if (term instanceof RdfTerm.LanguageLiteral languageLiteral) {
            return converter.makeLangLiteral(languageLiteral.lex(), languageLiteral.langtag());
        } else if (term instanceof RdfTerm.DtLiteral dtLiteral) {
            return converter.makeDtLiteral(dtLiteral.lex(), datatypeLookup.get(dtLiteral.datatype()));
        } else if (term instanceof RdfTerm.SimpleLiteral simpleLiteral) {
            return converter.makeSimpleLiteral(simpleLiteral.lex());
        } else if (term instanceof RdfTerm.Triple triple) {
            return converter.makeTripleNode(
                convertTerm(triple.subject()),
                convertTerm(triple.predicate()),
                convertTerm(triple.object())
            );
        } else {
            throw new RdfProtoDeserializationError("Unknown term type.");
        }
    }

    protected final TNode convertSubjectTermWrapped(RdfTerm.SpoTerm subject) {
        return convertSpoTermWrapped(subject, lastSubject);
    }

    protected final TNode convertPredicateTermWrapped(RdfTerm.SpoTerm predicate) {
        return convertSpoTermWrapped(predicate, lastPredicate);
    }

    protected final TNode convertObjectTermWrapped(RdfTerm.SpoTerm object) {
        return convertSpoTermWrapped(object, lastObject);
    }

    protected final TNode convertGraphTermWrapped(RdfTerm.GraphTerm graph) {
        if (graph == null) {
            return lastGraph.node == null ? null : lastGraph.node;
        } else {
            final var node = convertGraphTerm(graph);
            lastGraph.node = node;
            return node;
        }
    }

    protected final TNode convertTriple(RdfTerm.Triple triple) {
        return converter.makeTriple(
            convertSpoTermWrapped(triple.subject(), lastSubject),
            convertSpoTermWrapped(triple.predicate(), lastPredicate),
            convertSpoTermWrapped(triple.object(), lastObject)
        );
    }

    protected final TNode convertQuad(RdfTerm.Quad quad) {
        return converter.makeQuad(
            convertSpoTermWrapped(quad.subject(), lastSubject),
            convertSpoTermWrapped(quad.predicate(), lastPredicate),
            convertSpoTermWrapped(quad.object(), lastObject),
            convertGraphTermWrapped(quad.graph())
        );
    }

    private TNode convertSpoTermWrapped(RdfTerm.SpoTerm term, LastNodeHolder<TNode> lastNodeHolder) {
        if (term == null) {
            return lastNodeHolder.node == null ? null : lastNodeHolder.node;
        } else {
            final var node = convertTerm(term);
            lastNodeHolder.node = node;
            return node;
        }
    }
}
