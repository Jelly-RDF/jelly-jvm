package eu.neverblink.jelly.convert.titanium.internal;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.convert.titanium.TitaniumConstants;
import eu.neverblink.jelly.core.InternalApi;

/**
 * Internal representations of RDF literal data inside the Titanium converter.
 * <p>
 * These are not intended to be used outside of the converter's code.
 */
@InternalApi
public sealed interface TitaniumLiteral {
    TitaniumNode.TitaniumNodeType type();

    /**
     * The object of a quad passed to an RdfQuadConsumer, as a node of the converter: the IRI or
     * blank node string itself, or a literal.
     */
    static Object objectOf(String object, String datatype, String language, String direction) {
        if (!RdfQuadConsumer.isLiteral(datatype, language, direction)) {
            // IRIs and bnodes don't need further processing
            return object;
        }
        if (RdfQuadConsumer.isLangString(datatype, language, direction)) {
            return new LangLiteral(object, language);
        } else if (RdfQuadConsumer.isDirLangString(datatype, language, direction)) {
            return new DirLangLiteral(object, language, direction);
        } else if (datatype.equals(TitaniumConstants.DT_STRING)) {
            return new SimpleLiteral(object);
        }
        return new DtLiteral(object, datatype);
    }

    record SimpleLiteral(String lex) implements TitaniumLiteral {
        @Override
        public TitaniumNode.TitaniumNodeType type() {
            return TitaniumNode.TitaniumNodeType.SIMPLE_LITERAL;
        }
    }

    record LangLiteral(String lex, String lang) implements TitaniumLiteral {
        @Override
        public TitaniumNode.TitaniumNodeType type() {
            return TitaniumNode.TitaniumNodeType.LANG_LITERAL;
        }
    }

    /**
     * Directional language-tagged string (RDF 1.2).
     * @param direction "ltr" or "rtl", as in Titanium's RdfQuadConsumer
     */
    record DirLangLiteral(String lex, String lang, String direction) implements TitaniumLiteral {
        @Override
        public TitaniumNode.TitaniumNodeType type() {
            return TitaniumNode.TitaniumNodeType.DIR_LANG_LITERAL;
        }
    }

    record DtLiteral(String lex, String dt) implements TitaniumLiteral {
        @Override
        public TitaniumNode.TitaniumNodeType type() {
            return TitaniumNode.TitaniumNodeType.DT_LITERAL;
        }
    }
}
