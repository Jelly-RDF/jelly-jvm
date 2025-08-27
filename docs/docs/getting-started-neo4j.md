# Getting started with Neo4j

!!! info

    This integration with Neo4j requires the use of the [neosemantics](https://neo4j.com/labs/neosemantics/) plugin that only works with Neo4j Desktop and self-hosted instances (Neo4j Community/Enterprise). **It does not work with AuraDB.** For AuraDB, you can alternatively use the **[Python integration that connects to Neo4j over Bolt](https://w3id.org/jelly/pyjelly/dev/rdflib-neo4j-integration/)** via [rdflib-neo4j](https://neo4j.com/labs/rdflib-neo4j/).

## Installation

The **[latest version ({{ jvm_package_version() }}) of the Jelly-Neo4j plugin](https://github.com/Jelly-RDF/jelly-jvm/releases/download/v{{ jvm_package_version() }}/jelly-neo4j-plugin.jar)** is compatible with Neo4j versions {{ neo4j_version('min') }}–{{ neo4j_version('max') }}[^1]. Jelly-Neo4j depends on the [neosemantics](https://neo4j.com/labs/neosemantics/) plugin, which must also be installed. Neosemantics must have the same version as the Neo4j database.

=== "Neo4j Desktop"

    1. Install [Neo4j Desktop](https://neo4j.com/download/) if you haven't already.
    2. Create a new local instance. Select Neo4j version {{ neo4j_version('max') }}.
    3. In the list of instances, click on the :material-folder-outline: button next to "**Path:**" to open the instance folder.
    4. Open the `plugins` subfolder.
    5. Download the neosemantics plugin `.jar` file from the [neosemantics releases page](https://github.com/neo4j-labs/neosemantics/releases) and place it in the `plugins` folder.
    6. Download the [Jelly-Neo4j plugin `.jar` file](https://github.com/Jelly-RDF/jelly-jvm/releases/download/v{{ jvm_package_version() }}/jelly-neo4j-plugin.jar) and place it in the `plugins` folder.
    7. Go back to the Neo4j Desktop application and restart your instance.

=== "Self-hosted server (Community and Enterprise editions)"

    1. Install [Neo4j Community or Enterprise Edition](https://neo4j.com/docs/operations-manual/current/installation/) if you haven't already. The supported Neo4j versions are currently {{ neo4j_version('min') }}–{{ neo4j_version('max') }}.
    2. Open the Neo4j installation folder (`$NEO4J_HOME`).
    3. Open the `plugins` subfolder.
    4. Download the neosemantics plugin `.jar` file from the [neosemantics releases page](https://github.com/neo4j-labs/neosemantics/releases) and place it in the `plugins` folder. The plugin's version must be the same as Neo4j's.
    5. Download the [Jelly-Neo4j plugin `.jar` file](https://github.com/Jelly-RDF/jelly-jvm/releases/download/v{{ jvm_package_version() }}/jelly-neo4j-plugin.jar) and place it in the `plugins` folder.
    6. Start the Neo4j server.

At the end, your `plugins` folder should look like this:

```shell title="$ ls"
jelly-neo4j-plugin.jar  neosemantics-{{ neo4j_version('max') }}.jar  README.txt
```

See also the [installation instructions for neosemantics](https://neo4j.com/labs/neosemantics/installation/).

## Usage

The Jelly-Neo4j plugin adds support for Jelly to all functionalities of neosemantics. Two formats are supported:

- `Jelly` – native binary format. Supported in all places except aggregations due to Neo4j limitations.
- `Jelly-base64` – base64-encoded version of Jelly. Supported in all places.

### Setup

To get started with using the neosemantics plugin, changes must be made to database's constraints and graph config. **[See the instructions in the neosemantics documentation](https://neo4j.com/labs/neosemantics/tutorial/)**.

### Importing data

To import a Jelly file (either local or via HTTP), use the `n10s.rdf.import.fetch` procedure, with first argument set to the file URL or path, and the second set to `Jelly`. For example, to import RiverBench's metadata, call:

```cypher
CALL n10s.rdf.import.fetch(
    'https://w3id.org/riverbench/dumps-with-results/dev.jelly.gz', 
    'Jelly'
)
```

### Cypher aggregations

Results from Cypher queries can be aggregated into RDF data using the `n10s.rdf.export.cypher` procedure and `n10s.rdf.collect.jelly_base64` aggregation:

```cypher
CALL n10s.rdf.export.cypher("MATCH (p) RETURN p LIMIT 10")
yield subject, predicate, object, isLiteral, literalType, literalLang
return n10s.rdf.collect.jelly_base64(
    subject, predicate, object, isLiteral, literalType, literalLang
) as rdf
```

Note that the output here will be base64-encoded, due to Neo4j's limitations when working with binary data. To parse this, you will either need to use the `Jelly-base64` format in Neo4j, or decode it first from base64.

### Exporting data via HTTP

HTTP endpoints for bulk RDF export can be enabled in neosemantics by adding the following configuration to the `neo4j.conf` file:

```conf
server.unmanaged_extension_classes=n10s.endpoint=/rdf
```

Then, you can for example call the following endpoint to export the results of Cypher queries or describe individual nodes – simply append the `?format=Jelly` query parameter. For example, to describe the `https://w3id.org/riverbench/datasets/osm2rdf-denmark/dev` node, call:

```
GET /rdf/neo4j/describe/https%3A%2F%2Fw3id.org%2Friverbench%2Fdatasets%2Fosm2rdf-denmark%2Fdev?format=Jelly
```

The identifier was urlencoded. The result will be a Jelly file containing a description of the node.

See [the neosemantics documentation](https://neo4j.com/labs/neosemantics/) for more details and examples.

### Deleting data

In much the same way as importing, you can delete data with the `n10s.rdf.delete.fetch` procedure. For example, to delete all triples and quads from a Jelly file, call:

```cypher
CALL n10s.rdf.delete.fetch(
    'https://w3id.org/riverbench/dumps-with-results/dev.jelly.gz', 
    'Jelly'
)
```

### Checking the version of the plugin

To check the version of the Jelly-Neo4j plugin, call:

```cypher
RETURN jelly.version()
```

### More...

All features of neosemantics are supported. See the [neosemantics documentation](https://neo4j.com/labs/neosemantics/) for more details.

## See also

- [Alternative integration with Python and RDFLib](https://w3id.org/jelly/pyjelly/dev/rdflib-neo4j-integration/)
- [neosemantics documentation](https://neo4j.com/labs/neosemantics/)
- [Getting started with Jena and RDF4J plugins](getting-started-plugins.md)

[^1]: The limiting factor for compatibility is the neosemantics plugin, which must have the same version as the Neo4j database. Unfortunately, neosemantics is often much behind the latest Neo4j releases.
