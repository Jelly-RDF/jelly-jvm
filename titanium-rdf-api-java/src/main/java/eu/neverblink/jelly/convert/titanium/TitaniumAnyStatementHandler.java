package eu.neverblink.jelly.convert.titanium;

import static eu.neverblink.jelly.convert.titanium.TitaniumConstants.DT_LANG_STRING;
import static eu.neverblink.jelly.convert.titanium.TitaniumConstants.DT_STRING;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumNode;
import eu.neverblink.jelly.core.RdfHandler;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;

final class TitaniumAnyStatementHandler implements RdfHandler.AnyStatementHandler<TitaniumNode> {

    private RdfQuadConsumer consumer;

    @Override
    public void handleQuad(TitaniumNode s, TitaniumNode p, TitaniumNode o, TitaniumNode g) {
        try {
            switch (o.type()) {
                case IRI, BLANK -> consumer.quad(
                    s.asStringValue(),
                    p.asStringValue(),
                    o.asStringValue(),
                    null,
                    null,
                    null,
                    g != null ? g.asStringValue() : null
                );
                case LANG_LITERAL -> {
                    final var langLiteral = o.asLangLiteral();
                    consumer.quad(
                        s.asStringValue(),
                        p.asStringValue(),
                        langLiteral.lex(),
                        DT_LANG_STRING,
                        langLiteral.lang(),
                        null,
                        g != null ? g.asStringValue() : null
                    );
                }
                case SIMPLE_LITERAL -> {
                    var simpleLiteral = o.asSimpleLiteral();
                    consumer.quad(
                        s.asStringValue(),
                        p.asStringValue(),
                        simpleLiteral.lex(),
                        DT_STRING,
                        null,
                        null,
                        g != null ? g.asStringValue() : null
                    );
                }
                case DT_LITERAL -> {
                    var dtLiteral = o.asDtLiteral();
                    consumer.quad(
                        s.asStringValue(),
                        p.asStringValue(),
                        dtLiteral.lex(),
                        dtLiteral.dt(),
                        null,
                        null,
                        g != null ? g.asStringValue() : null
                    );
                }
            }
        } catch (Exception e) {
            throw new RdfProtoDeserializationError("Could not parse generalized quad statement", e);
        }
    }

    @Override
    public void handleTriple(TitaniumNode s, TitaniumNode p, TitaniumNode o) {
        try {
            switch (o.type()) {
                case IRI, BLANK -> consumer.quad(
                    s.asStringValue(),
                    p.asStringValue(),
                    o.asStringValue(),
                    null,
                    null,
                    null,
                    null
                );
                case LANG_LITERAL -> {
                    final var langLiteral = o.asLangLiteral();
                    consumer.quad(
                        s.asStringValue(),
                        p.asStringValue(),
                        langLiteral.lex(),
                        DT_LANG_STRING,
                        langLiteral.lang(),
                        null,
                        null
                    );
                }
                case SIMPLE_LITERAL -> {
                    var simpleLiteral = o.asSimpleLiteral();
                    consumer.quad(
                        s.asStringValue(),
                        p.asStringValue(),
                        simpleLiteral.lex(),
                        DT_STRING,
                        null,
                        null,
                        null
                    );
                }
                case DT_LITERAL -> {
                    var dtLiteral = o.asDtLiteral();
                    consumer.quad(
                        s.asStringValue(),
                        p.asStringValue(),
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
