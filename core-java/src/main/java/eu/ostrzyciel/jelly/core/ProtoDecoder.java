package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.internal.NameDecoder;
import eu.ostrzyciel.jelly.core.internal.ProtoDecoderBase;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;
import java.util.Optional;

public abstract class ProtoDecoder<TNode, TDatatype, TTriple, TQuad, TOut>
    extends ProtoDecoderBase<TNode, TDatatype, TTriple, TQuad> {

    protected ProtoDecoder(
        Class<TDatatype> datatypeClass,
        ProtoDecoderConverter<TNode, TDatatype, TTriple, TQuad> converter,
        NameDecoder<TNode> nameDecoder
    ) {
        super(datatypeClass, converter, nameDecoder);
    }

    protected abstract Optional<RdfStreamOptions> getStreamOptions();

    public abstract TOut ingestRowFlat(RdfStreamRow row);

    public final Optional<TOut> ingestRow(RdfStreamRow row) {
        var flat = ingestRowFlat(row);
        return Optional.ofNullable(flat);
    }
}
