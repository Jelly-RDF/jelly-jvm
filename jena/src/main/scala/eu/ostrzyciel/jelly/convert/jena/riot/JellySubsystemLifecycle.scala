package eu.ostrzyciel.jelly.convert.jena.riot

import org.apache.jena.sys.JenaSubsystemLifecycle

/**
 * Subsystem lifecycle for registering the Jelly language in Jena.
 * See: https://jena.apache.org/documentation/notes/system-initialization.html
 */
class JellySubsystemLifecycle extends JenaSubsystemLifecycle:
  override def start(): Unit = JellyLanguage.register()

  override def stop(): Unit = ()

  // "Application code should use level above 500."
  // Not clear whether the plugins should use > 500 or < 500...
  // Let's use 501, this should make Jelly load before the other application subsystems.
  override def level() = 501
