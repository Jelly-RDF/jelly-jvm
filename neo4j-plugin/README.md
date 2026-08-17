# Jelly plugin for Neo4j

> [!WARNING]
> **This plugin is currently parked and is not built, tested, or released.**
>
> It runs inside Neo4j next to the [Neosemantics](https://github.com/neo4j-labs/neosemantics)
> plugin, which bundles RDF4J 4.3.12 in its fat JAR. Since `jelly-rdf4j` moved to RDF4J 6, the two
> can no longer coexist:
>
> - RDF4J 6 requires Java 25, while Neo4j 5.26 targets Java 17.
> - The bundled RDF4J 4.3.12 has neither `IntegerRioSetting` nor `TripleTerm`, both of which
>   `jelly-rdf4j` now uses unconditionally.
>
> The sources are kept so the integration can be revived once Neosemantics ships a modern RDF4J.
> Until then, use the last RDF4J-5-based Jelly-JVM release for Neo4j. To build this module locally,
> pin `rdf4jV` in `build.sbt` back to a 5.x release.

See the documentation for installation and usage instructions:
https://w3id.org/jelly/jelly-jvm/dev/getting-started-neo4j/
