package eu.neverblink.jelly.convert.titanium;

import static eu.neverblink.jelly.convert.titanium.TitaniumConstants.DT_LANG_STRING;
import static eu.neverblink.jelly.convert.titanium.TitaniumConstants.DT_STRING;

import com.apicatalog.rdf.api.RdfConsumerException;
import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumNode;
import eu.neverblink.jelly.core.RdfHandler;

final class TitaniumAnyStatementHandler implements RdfHandler.AnyStatementHandler<TitaniumNode> {

    private RdfQuadConsumer consumer;

    @Override
    public void handleQuad(TitaniumNode s, TitaniumNode p, TitaniumNode o, TitaniumNode g) {
        try {
            switch (o.type()) {
                case IRI:
                    consumer.quad(
                        s.asStringValue(),
                        p.asStringValue(),
                        o.asStringValue(),
                        null,
                        null,
                        null,
                        g.asStringValue()
                    );
                    break;
                case LANG_LITERAL:
                    final var langLiteral = o.asLangLiteral();
                    consumer.quad(
                        s.asStringValue(),
                        p.asStringValue(),
                        langLiteral.lex(),
                        DT_LANG_STRING,
                        langLiteral.lang(),
                        null,
                        g.asStringValue()
                    );
                    break;
                case SIMPLE_LITERAL:
                    var simpleLiteral = o.asSimpleLiteral();
                    consumer.quad(
                        s.asStringValue(),
                        p.asStringValue(),
                        simpleLiteral.lex(),
                        DT_STRING,
                        null,
                        null,
                        g.asStringValue()
                    );
                    break;
                case DT_LITERAL:
                    var dtLiteral = o.asDtLiteral();
                    consumer.quad(
                        s.asStringValue(),
                        p.asStringValue(),
                        dtLiteral.lex(),
                        dtLiteral.dt(),
                        null,
                        null,
                        g.asStringValue()
                    );
                    break;
            }
        } catch (RdfConsumerException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void handleTriple(TitaniumNode s, TitaniumNode p, TitaniumNode o) {
        throw new UnsupportedOperationException("Triple not supported in Titanium Jelly");
    }

    public void assignConsumer(RdfQuadConsumer consumer) {
        this.consumer = consumer;
    }
}
