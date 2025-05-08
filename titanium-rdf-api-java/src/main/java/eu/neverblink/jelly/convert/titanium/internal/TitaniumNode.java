package eu.neverblink.jelly.convert.titanium.internal;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.core.InternalApi;

/**
 * Internal tools to represent RDF data inside the Titanium converter.
 * <p>
 * These are not intended to be used outside of the converter's code.
 */
@InternalApi
public final class TitaniumNode {

    private TitaniumNode() {}

    public enum TitaniumNodeType {
        IRI,
        BLANK,
        SIMPLE_LITERAL,
        LANG_LITERAL,
        DT_LITERAL,
    }

    public static TitaniumNodeType typeOf(Object node) {
        if ((node instanceof String iriLike)) {
            return RdfQuadConsumer.isBlank(iriLike) ? TitaniumNodeType.BLANK : TitaniumNodeType.IRI;
        }

        if ((node instanceof TitaniumLiteral literal)) {
            return literal.type();
        }

        return null;
    }

    public static String iriLikeOf(Object node) {
        return (String) node;
    }

    public static TitaniumLiteral.SimpleLiteral simpleLiteralOf(Object node) {
        return (TitaniumLiteral.SimpleLiteral) node;
    }

    public static TitaniumLiteral.LangLiteral langLiteralOf(Object node) {
        return (TitaniumLiteral.LangLiteral) node;
    }

    public static TitaniumLiteral.DtLiteral dtLiteralOf(Object node) {
        return (TitaniumLiteral.DtLiteral) node;
    }
}
