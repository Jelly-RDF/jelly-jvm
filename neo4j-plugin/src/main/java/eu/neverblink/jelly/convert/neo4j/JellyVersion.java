package eu.neverblink.jelly.convert.neo4j;

import org.neo4j.procedure.Description;
import org.neo4j.procedure.UserFunction;

public class JellyVersion {
    static {
        // Call the initializer of the plugin to ensure any setup is done
        JellyPlugin.getInstance().initialize();
    }

    @UserFunction("jelly.version")
    @Description("RETURN jelly.version() | return the version of the Jelly-JVM library")
    public String version() {
        String version = JellyVersion.class.getPackage().getImplementationVersion();
        if (version == null) {
            version = "dev";
        }
        return version;
    }
}
