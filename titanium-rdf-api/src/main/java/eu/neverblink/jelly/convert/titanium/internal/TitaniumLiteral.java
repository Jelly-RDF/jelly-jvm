package eu.neverblink.jelly.convert.titanium.internal;

import eu.neverblink.jelly.core.InternalApi;

/**
 * Internal representations of RDF literal data inside the Titanium converter.
 * <p>
 * These are not intended to be used outside of the converter's code.
 */
@InternalApi
public sealed interface TitaniumLiteral {
    TitaniumNode.TitaniumNodeType type();

    record SimpleLiteral(String lex) implements TitaniumLiteral {
        @Override
        public TitaniumNode.TitaniumNodeType type() {
            return TitaniumNode.TitaniumNodeType.SIMPLE_LITERAL;
        }
    }

    // No support for RDF 1.2 directionality... yet.
    record LangLiteral(String lex, String lang) implements TitaniumLiteral {
        @Override
        public TitaniumNode.TitaniumNodeType type() {
            return TitaniumNode.TitaniumNodeType.LANG_LITERAL;
        }
    }

    record DtLiteral(String lex, String dt) implements TitaniumLiteral {
        @Override
        public TitaniumNode.TitaniumNodeType type() {
            return TitaniumNode.TitaniumNodeType.DT_LITERAL;
        }
    }
}
