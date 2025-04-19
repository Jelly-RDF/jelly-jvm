package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.ProtoDecoderConverter;
import eu.ostrzyciel.jelly.core.RdfProtoDeserializationError;
import eu.ostrzyciel.jelly.core.RdfTerm;

public abstract class ProtoDecoderBase<TNode, TDatatype, TTriple, TQuad> {

    protected final ProtoDecoderConverter<TNode, TDatatype, TTriple, TQuad> converter;
    protected final NameDecoder<TNode> nameDecoder;
    protected final DecoderLookup<TDatatype> datatypeLookup;

    protected final Class<TDatatype> datatypeClass;

    protected final LastNodeHolder<TNode> lastSubject = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastPredicate = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastObject = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastGraph = new LastNodeHolder<>();

    protected ProtoDecoderBase(
        Class<TDatatype> datatypeClass,
        ProtoDecoderConverter<TNode, TDatatype, TTriple, TQuad> converter,
        NameDecoder<TNode> nameDecoder
    ) {
        this.datatypeClass = datatypeClass;
        this.converter = converter;
        this.nameDecoder = nameDecoder;
        this.datatypeLookup = new DecoderLookup<>(datatypeClass, getDatatypeTableSize());
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

    protected final TNode convertTermWrapped(RdfTerm.SpoTerm term, LastNodeHolder<TNode> lastNodeHolder) {
        if (term == null) {
            return lastNodeHolder.node == null ? null : lastNodeHolder.node;
        } else {
            final var node = convertTerm(term);
            lastNodeHolder.node = node;
            return node;
        }
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

    protected final TTriple convertTriple(RdfTerm.Triple triple) {
        return converter.makeTriple(
            convertTermWrapped(triple.subject(), lastSubject),
            convertTermWrapped(triple.predicate(), lastPredicate),
            convertTermWrapped(triple.object(), lastObject)
        );
    }

    protected final TQuad convertQuad(RdfTerm.Quad quad) {
        return converter.makeQuad(
            convertTermWrapped(quad.subject(), lastSubject),
            convertTermWrapped(quad.predicate(), lastPredicate),
            convertTermWrapped(quad.object(), lastObject),
            convertGraphTermWrapped(quad.graph())
        );
    }
}
