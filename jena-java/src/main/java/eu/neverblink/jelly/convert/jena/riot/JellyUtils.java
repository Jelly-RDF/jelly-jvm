package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RiotException;
import org.apache.jena.sparql.util.Context;

class JellyUtils {

    private JellyUtils() {}

    /**
     * Extract the format variant from the RDFFormat or use the default.
     */
    public static JellyFormatVariant getVariant(RDFFormat syntaxForm) {
        if (syntaxForm.getVariant() instanceof JellyFormatVariant jellyFormatVariant) {
            return jellyFormatVariant;
        } else {
            return new JellyFormatVariant();
        }
    }

    /**
     * Update the Jelly format variant with the information from the context.
     * @param baseVariant base variant
     * @param context context
     * @return updated variant
     */
    public static JellyFormatVariant applyContext(JellyFormatVariant baseVariant, Context context) {
        // Use the preset if set
        final var presetName = context.get(JellyLanguage.SYMBOL_PRESET, "");
        RdfStreamOptions preset;
        if (!presetName.isEmpty()) {
            preset = JellyLanguage.PRESETS.get(presetName);
            if (preset == null) {
                throw new RiotException(
                    "Unknown Jelly preset: %s. Available presets: %s".formatted(
                            presetName,
                            String.join(", ", JellyLanguage.PRESETS.keySet())
                        )
                );
            }
        } else {
            preset = baseVariant.getOptions();
        }

        return new JellyFormatVariant(
            context.get(JellyLanguage.SYMBOL_STREAM_OPTIONS, preset),
            context.isTrue(JellyLanguage.SYMBOL_ENABLE_NAMESPACE_DECLARATIONS) ||
            baseVariant.isEnableNamespaceDeclarations(),
            context.isTrueOrUndef(JellyLanguage.SYMBOL_DELIMITED_OUTPUT),
            context.getInt(JellyLanguage.SYMBOL_FRAME_SIZE, baseVariant.getFrameSize())
        );
    }
}
