package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.ReaderRIOT;
import org.apache.jena.riot.ReaderRIOTFactory;
import org.apache.jena.riot.system.ParserProfile;

public final class JellyReaderFactory implements ReaderRIOTFactory {

    @Override
    public ReaderRIOT create(Lang language, ParserProfile profile) {
        final var converterFactory = JenaConverterFactory.getInstance();
        return new JellyReader(converterFactory);
    }
}
