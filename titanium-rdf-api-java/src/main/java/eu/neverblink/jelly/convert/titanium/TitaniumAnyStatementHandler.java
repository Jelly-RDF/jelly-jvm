package eu.neverblink.jelly.convert.titanium;

import static eu.neverblink.jelly.convert.titanium.TitaniumConstants.DT_LANG_STRING;
import static eu.neverblink.jelly.convert.titanium.TitaniumConstants.DT_STRING;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumNode;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.RdfHandler;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;

@InternalApi
final class TitaniumAnyStatementHandler implements RdfHandler.AnyStatementHandler<Object> {

    private RdfQuadConsumer consumer;

    @Override
    public void handleQuad(Object s, Object p, Object o, Object g) {
        try {
            switch (TitaniumNode.typeOf(o)) {
                case IRI, BLANK -> consumer.quad(
                    TitaniumNode.iriLikeOf(s),
                    TitaniumNode.iriLikeOf(p),
                    TitaniumNode.iriLikeOf(o),
                    null,
                    null,
                    null,
                    TitaniumNode.iriLikeOf(g)
                );
                case LANG_LITERAL -> {
                    final var langLiteral = TitaniumNode.langLiteralOf(o);
                    consumer.quad(
                        TitaniumNode.iriLikeOf(s),
                        TitaniumNode.iriLikeOf(p),
                        langLiteral.lex(),
                        DT_LANG_STRING,
                        langLiteral.lang(),
                        null,
                        TitaniumNode.iriLikeOf(g)
                    );
                }
                case SIMPLE_LITERAL -> {
                    var simpleLiteral = TitaniumNode.simpleLiteralOf(o);
                    consumer.quad(
                        TitaniumNode.iriLikeOf(s),
                        TitaniumNode.iriLikeOf(p),
                        simpleLiteral.lex(),
                        DT_STRING,
                        null,
                        null,
                        TitaniumNode.iriLikeOf(g)
                    );
                }
                case DT_LITERAL -> {
                    var dtLiteral = TitaniumNode.dtLiteralOf(o);
                    consumer.quad(
                        TitaniumNode.iriLikeOf(s),
                        TitaniumNode.iriLikeOf(p),
                        dtLiteral.lex(),
                        dtLiteral.dt(),
                        null,
                        null,
                        TitaniumNode.iriLikeOf(g)
                    );
                }
            }
        } catch (Exception e) {
            throw new RdfProtoDeserializationError("Could not parse generalized quad statement", e);
        }
    }

    @Override
    public void handleTriple(Object s, Object p, Object o) {
        try {
            switch (TitaniumNode.typeOf(o)) {
                case IRI, BLANK -> consumer.quad(
                    TitaniumNode.iriLikeOf(s),
                    TitaniumNode.iriLikeOf(p),
                    TitaniumNode.iriLikeOf(o),
                    null,
                    null,
                    null,
                    null
                );
                case LANG_LITERAL -> {
                    final var langLiteral = TitaniumNode.langLiteralOf(o);
                    consumer.quad(
                        TitaniumNode.iriLikeOf(s),
                        TitaniumNode.iriLikeOf(p),
                        langLiteral.lex(),
                        DT_LANG_STRING,
                        langLiteral.lang(),
                        null,
                        null
                    );
                }
                case SIMPLE_LITERAL -> {
                    var simpleLiteral = TitaniumNode.simpleLiteralOf(o);
                    consumer.quad(
                        TitaniumNode.iriLikeOf(s),
                        TitaniumNode.iriLikeOf(p),
                        simpleLiteral.lex(),
                        DT_STRING,
                        null,
                        null,
                        null
                    );
                }
                case DT_LITERAL -> {
                    var dtLiteral = TitaniumNode.dtLiteralOf(o);
                    consumer.quad(
                        TitaniumNode.iriLikeOf(s),
                        TitaniumNode.iriLikeOf(p),
                        dtLiteral.lex(),
                        dtLiteral.dt(),
                        null,
                        null,
                        null
                    );
                }
            }
        } catch (Exception e) {
            throw new RdfProtoDeserializationError("Could not parse generalized triple statement", e);
        }
    }

    public void assignConsumer(RdfQuadConsumer consumer) {
        this.consumer = consumer;
    }
}
