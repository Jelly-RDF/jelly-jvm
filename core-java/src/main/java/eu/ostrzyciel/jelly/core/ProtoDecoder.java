package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.internal.NameDecoder;
import eu.ostrzyciel.jelly.core.internal.ProtoDecoderBase;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;

public abstract class ProtoDecoder<TNode, TDatatype, TTriple, TQuad, TOut>
    extends ProtoDecoderBase<TNode, TDatatype, TTriple, TQuad> {

    protected ProtoDecoder(
        ProtoDecoderConverter<TNode, TDatatype, TTriple, TQuad> converter,
        NameDecoder<TNode> nameDecoder
    ) {
        super(converter, nameDecoder);
    }

    protected abstract RdfStreamOptions getStreamOptions();

    public abstract TOut ingestRow(RdfStreamRow row);
}
