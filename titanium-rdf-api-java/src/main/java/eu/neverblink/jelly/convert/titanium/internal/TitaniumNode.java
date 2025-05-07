package eu.neverblink.jelly.convert.titanium.internal;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.core.InternalApi;

/**
 * Internal representations of RDF data inside the Titanium converter.
 * <p>
 * These are not intended to be used outside of the converter's code.
 */
@InternalApi
public sealed interface TitaniumNode {
    enum TitaniumNodeType {
        IRI,
        BLANK,
        SIMPLE_LITERAL,
        LANG_LITERAL,
        DT_LITERAL,
    }

    TitaniumNodeType type();

    default StringNode asString() {
        return (StringNode) this;
    }

    default String asStringValue() {
        return asString().value();
    }

    default SimpleLiteral asSimpleLiteral() {
        return (SimpleLiteral) this;
    }

    default LangLiteral asLangLiteral() {
        return (LangLiteral) this;
    }

    default DtLiteral asDtLiteral() {
        return (DtLiteral) this;
    }

    sealed interface TitaniumLiteral extends TitaniumNode {}

    // String used to represent IRIs and blank nodes
    record StringNode(String value) implements TitaniumNode {
        @Override
        public TitaniumNodeType type() {
            return RdfQuadConsumer.isBlank(value) ? TitaniumNodeType.BLANK : TitaniumNodeType.IRI;
        }
    }

    record SimpleLiteral(String lex) implements TitaniumLiteral {
        @Override
        public TitaniumNodeType type() {
            return TitaniumNodeType.SIMPLE_LITERAL;
        }
    }

    // No support for RDF 1.2 directionality... yet.
    record LangLiteral(String lex, String lang) implements TitaniumLiteral {
        @Override
        public TitaniumNodeType type() {
            return TitaniumNodeType.LANG_LITERAL;
        }
    }

    record DtLiteral(String lex, String dt) implements TitaniumLiteral {
        @Override
        public TitaniumNodeType type() {
            return TitaniumNodeType.DT_LITERAL;
        }
    }
}
