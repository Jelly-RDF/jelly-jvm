package eu.neverblink.jelly.convert.titanium.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.JellyConverterFactory;

/**
 * A singleton factory for creating Jelly Titanium converters.
 * <p>
 * This class is a singleton and should be accessed via the {@link #getInstance()} method.
 */
@InternalApi
public final class TitaniumConverterFactory
    extends JellyConverterFactory<Object, String, TitaniumEncoderConverter, TitaniumDecoderConverter> {

    private static final TitaniumConverterFactory INSTANCE = new TitaniumConverterFactory();

    private TitaniumConverterFactory() {}

    /**
     * Returns the singleton instance of the {@link TitaniumConverterFactory}.
     *
     * @return the singleton instance of the {@link TitaniumConverterFactory}
     */
    public static TitaniumConverterFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public TitaniumEncoderConverter encoderConverter() {
        return new TitaniumEncoderConverter();
    }

    @Override
    public TitaniumDecoderConverter decoderConverter() {
        return new TitaniumDecoderConverter();
    }
}
