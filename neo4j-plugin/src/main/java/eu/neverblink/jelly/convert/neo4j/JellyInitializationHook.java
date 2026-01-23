package eu.neverblink.jelly.convert.neo4j;

import org.neo4j.configuration.SettingsDeclaration;

/**
 * A class that ensures the JellyPlugin is initialized when Neo4j is starting up.
 * We pretend to be a SettingsDeclaration, which is loaded via ServiceLoader,
 * to get our static initializer called.
 * <p>
 * This is a bit of a hack, but Neo4j does not provide a better way to hook into its startup process.
 */
public final class JellyInitializationHook implements SettingsDeclaration {

    static {
        // Call the initializer of the plugin to ensure any setup is done
        JellyPlugin.getInstance().initialize();
    }
}
