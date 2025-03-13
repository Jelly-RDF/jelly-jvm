The `jelly-titanium-rdf-api` module integrates Jelly with the minimalistic [Titanium RDF API](https://github.com/filip26/titanium-rdf-api). This API is by itself not a fully-fledged RDF library, but is rather intended as an interoperability bridge. 

If you are already using Jena or RDF4J, you should use the [`jelly-jena`](jena.md) or [`jelly-rdf4j`](rdf4j.md) module instead. This way you'll get better performance and more features.

## Installation

`jelly-titanium-rdf-api` has a pure Java API and is available on Maven Central. To include it in your project, add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>eu.ostrzyciel.jelly</groupId>
    <artifactId>jelly-titanium-rdf-api</artifactId>
    <version>{{ jvm_package_version() }}</version>
</dependency>
```

Note that while the API is in Java, the code does depend on Scala. The dependency on Scala is expected to be removed in Jelly-JVM 3.0.0.

## Basic I/O operations

The module implements a simple Jelly file reader and writer for Titanium. See the classes {{ javadoc_link_pretty('titanium', 'JellyTitaniumReader') }} and {{ javadoc_link_pretty('titanium', 'JellyTitaniumWriter') }}. You should simply instantiate them using the `.factory` static method and then either push quads into the writer, or instruct the reader to push quads into another quad consumer.

Full example of integration with the [`titanium-rdf-n-quads`](https://github.com/filip26/titanium-rdf-n-quads) library:

{{ code_example('TitaniumRdfApi.java') }}

## Low-level usage

Titanium RDF API does not implement types for RDF primitives, so the Jelly integration with it is a bit different from the ones for Jena and RDF4J. Currently, the Pekko Streams API is not supported, and the `ConverterFactory` for Titanium is not part of the public API.

But, you can still access a part of the low-level API directly. This would be useful if you wanted to integrate Titanium with Kafka or some other custom serialization pipeline.

To do this, use the {{ javadoc_link_pretty('titanium', 'JellyTitaniumDecoder') }} and {{ javadoc_link_pretty('titanium', 'JellyTitaniumEncoder') }} classes directly.

## See also

- [Useful utilities](utilities.md)
