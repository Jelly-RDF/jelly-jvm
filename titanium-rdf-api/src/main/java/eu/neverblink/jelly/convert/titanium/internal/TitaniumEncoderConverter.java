package eu.neverblink.jelly.convert.titanium.internal;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;

/**
 * Converter for translating between Titanium RDF API nodes/terms and Jelly proto objects.
 * <p>
 * IRIs and blank nodes are both strings, blank nodes with the "_:" prefix. Literals are
 * {@link TitaniumLiteral}s.
 */
@InternalApi
public final class TitaniumEncoderConverter implements ProtoEncoderConverter<Object> {

    @Override
    public void encodeIri(NodeEncoder<Object> encoder, Object node) {
        // The check for an IRI is the same as for a resource
        encodeResource(encoder, node);
    }

    @Override
    public void encodeResource(NodeEncoder<Object> encoder, Object node) {
        if (node instanceof String iriLike) {
            encodeIriLike(encoder, iriLike);
        } else {
            encodeAny(encoder, node);
        }
    }

    @Override
    public void encodeGraph(NodeEncoder<Object> encoder, Object node) {
        if (node == null) {
            encoder.defaultGraph();
        } else {
            encodeResource(encoder, node);
        }
    }

    @Override
    public void encodeAny(NodeEncoder<Object> encoder, Object node) {
        if (node instanceof String iriLike) {
            encodeIriLike(encoder, iriLike);
        } else if (node instanceof TitaniumLiteral literal) {
            encodeLiteral(encoder, literal);
        } else {
            throw new RdfProtoSerializationError("Cannot encode node: %s".formatted(node));
        }
    }

    private static void encodeIriLike(NodeEncoder<Object> encoder, String iriLike) {
        if (RdfQuadConsumer.isBlank(iriLike)) {
            // remove "_:"
            encoder.blankNode(iriLike.substring(2));
        } else {
            encoder.iri(iriLike);
        }
    }

    private static void encodeLiteral(NodeEncoder<Object> encoder, TitaniumLiteral literal) {
        switch (literal) {
            case TitaniumLiteral.SimpleLiteral l -> encoder.simpleLiteral(l.lex());
            case TitaniumLiteral.LangLiteral l -> encoder.langLiteral(l.lex(), l.lang());
            case TitaniumLiteral.DirLangLiteral l -> encoder.dirLangLiteral(
                l.lex(),
                l.lang(),
                baseDirection(l.direction())
            );
            case TitaniumLiteral.DtLiteral l -> encoder.dtLiteral(l.lex(), l.dt());
        }
    }

    private static RdfBaseDirection baseDirection(String direction) {
        return switch (direction) {
            case "ltr" -> RdfBaseDirection.LTR;
            case "rtl" -> RdfBaseDirection.RTL;
            default -> throw new RdfProtoSerializationError(
                "Unknown base direction: '%s'. Expected 'ltr' or 'rtl'.".formatted(direction)
            );
        };
    }
}
