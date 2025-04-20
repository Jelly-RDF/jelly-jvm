package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.internal.ProtoDecoderBase;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;

public abstract class ProtoDecoder<TNode, TDatatype>
    extends ProtoDecoderBase<TNode, TDatatype> {

    protected ProtoDecoder(
        ProtoDecoderConverter<TNode, TDatatype> converter
    ) {
        super(converter);
    }

    protected abstract RdfStreamOptions getStreamOptions();

    public abstract void ingestRow(RdfStreamRow row);
}
