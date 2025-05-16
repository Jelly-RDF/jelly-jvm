package eu.neverblink.jelly.convert.jena.riot;

import org.apache.jena.sys.JenaSubsystemLifecycle;

public final class JellySubsystemLifecycle implements JenaSubsystemLifecycle {

    @Override
    public void start() {
        JellyLanguage.register();
    }

    @Override
    public void stop() {
        // No-op
    }

    @Override
    public int level() {
        // "Application code should use level above 500."
        // Not clear whether the plugins should use > 500 or < 500...
        // Let's use 501, this should make Jelly load before the other application subsystems.
        return 501;
    }
}
