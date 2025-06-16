The `jelly-titanium-rdf-api` module integrates Jelly with the minimalistic [Titanium RDF API](https://github.com/filip26/titanium-rdf-api). This API is by itself not a fully-fledged RDF library, but is rather intended as an interoperability bridge. 

If you are already using Jena or RDF4J, you should use the [`jelly-jena`](jena.md) or [`jelly-rdf4j`](rdf4j.md) module instead. This way you'll get better performance and more features.

## Installation

`jelly-titanium-rdf-api` is available on Maven Central. To include it in your project, add the following dependency to your `pom.xml`:

=== "Maven"

    ```xml title="pom.xml"
    <dependency>
        <groupId>eu.neverblink.jelly</groupId>
        <artifactId>jelly-titanium-rdf-api</artifactId>
        <version>{{ jvm_package_version() }}</version>
    </dependency>
    ```

=== "Gradle"

    ```groovy title="build.gradle"
    dependencies {
        implementation 'eu.neverblink.jelly:jelly-titanium-rdf-api:{{ jvm_package_version() }}'
    }
    ```

=== "SBT"

    ```scala title="build.sbt"
    libraryDependencies += "eu.neverblink.jelly" % "jelly-titanium-rdf-api" % "{{ jvm_package_version() }}"
    ```

## Basic I/O operations

The module implements a simple Jelly file reader and writer for Titanium. See the classes {{ javadoc_link_pretty('titanium-rdf-api', 'TitaniumJellyReader') }} and {{ javadoc_link_pretty('titanium-rdf-api', 'TitaniumJellyWriter') }}. You should simply instantiate them using the `.factory` static method and then either push quads into the writer, or instruct the reader to push quads into another quad consumer.

Full example of integration with the [`titanium-rdf-n-quads`](https://github.com/filip26/titanium-rdf-n-quads) library:

{{ code_example('TitaniumRdfApi.java') }}

## Low-level usage

Titanium RDF API does not implement types for RDF primitives, so the Jelly integration with it is a bit different from the ones for Jena and RDF4J. Currently, the [Pekko Streams API](reactive.md) is not supported, and the `JellyConverterFactory` for Titanium is not part of the public API.

But, you can still access a part of the low-level API directly. This would be useful if you wanted to integrate Titanium with Kafka or some other custom serialization pipeline.

To do this, use the {{ javadoc_link_pretty('titanium-rdf-api', 'TitaniumJellyDecoder') }} and {{ javadoc_link_pretty('titanium-rdf-api', 'TitaniumJellyEncoder') }} classes directly.

## Integrations

Jelly-JVM implements the `RdfQuadConsumer` interface, so you can hook it up to any library that does the same. This includes formats like: [JSON-LD](https://github.com/filip26/titanium-json-ld), [CBOR-LD](https://github.com/filip26/iridium-cbor-ld), [N-Quads](https://github.com/filip26/titanium-rdf-n-quads).

## See also

- [Useful utilities](utilities.md)
