package eu.neverblink.jelly.convert.jena.sparql;

import org.apache.jena.sys.JenaSubsystemLifecycle;

public final class JellySparqlSubsystemLifecycle implements JenaSubsystemLifecycle {

    @Override
    public void start() {
        JellySparqlLanguage.register();
    }

    @Override
    public void stop() {
        // No-op
    }

    @Override
    public int level() {
        // Same level as the main Jelly language registration in the jena module.
        return 501;
    }
}
