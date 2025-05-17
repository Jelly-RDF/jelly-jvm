# Useful utilities

This guide presents some useful utilities in the `jelly-core` and `jelly-pekko-stream` modules.

## Jelly options presets

Every Jelly stream begins with a header that specifies the serialization options used to encode the stream – [see the details in the specification]({{ proto_link('specification/serialization#stream-options') }}). So, whenever you serialize some RDF with Jelly (e.g., using [Apache Jena RIOT](jena.md), [RDF4J Rio](rdf4j.md), or the [`jelly-pekko-stream` module](reactive.md)), you need to specify these options.

The {{ javadoc_link_pretty('core', 'JellyOptions') }} object provides a few common presets for Jelly serialization options. They return an instance of {{ javadoc_link_pretty('core', 'proto.v1.RdfStreamOptions') }} that you can further customize. For example:

=== "Java"

    ```java title="Java example"
    import eu.neverblink.jelly.core.JellyOptions;
    
    RdfStreamOptions options = JellyOptions.SMALL_STRICT;
    
    RdfStreamOptions optionsWithRdfStarSupport = JellyOptions.SMALL_RDF_STAR;
    
    RdfStreamOptions bigWithCustomDictionarySize = JellyOptions.BIG_STRICT
      .clone()
      .setMaxNameTableSize(2000);
    ```

=== "Scala"

    ```scala title="Scala example"
    import eu.neverblink.jelly.core.JellyOptions
    
    val options = JellyOptions.SMALL_STRICT
    
    val optionsWithRdfStarSupport = JellyOptions.SMALL_RDF_STAR
      
    val bigWithCustomDictionarySize = JellyOptions.BIG_STRICT
      .clone()
      .setMaxNameTableSize(2000)  
    ```

!!! warning 
    
    These presets **do not** specify the physical or logical stream type. In most cases, the Jelly library will take care of this for you and set these types automatically later. However, if you use the [low-level API](low-level.md), you need to set the stream types manually. For example:

    === "Java"

        ```java title="Java example"
        import eu.neverblink.jelly.core.JellyOptions;
        import eu.neverblink.jelly.core.proto.v1.*;
    
        JellyOptions.SMALL_STRICT
          .clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.DATASETS);
        ```

    === "Scala"

        ```scala title="Scala example"
        import eu.neverblink.jelly.core.JellyOptions
        import eu.neverblink.jelly.core.proto.v1.*
    
        JellyOptions.SMALL_STRICT
          .clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.DATASETS)
        ```

## Checking supported options

There is also the {{ javadoc_link_pretty('core', 'JellyOptions', 'DEFAULT_SUPPORTED_OPTIONS') }} field which specifies the maximum set of options supported by default in Jelly-JVM, when parsing a stream. By default, Jelly-JVM will refuse to parse any stream that uses options that are beyond what is specified in this method. This is important for security reasons, as it prevents the library from, for example, allocating a 10 GB dictionary (potential Denial of Service attack).

The supported options check is carried out automatically by the decoder when parsing a stream. You cannot disable the check, but you can customize the supported options by constructing a new `RdfStreamOptions` object from {{ javadoc_link_pretty('core', 'JellyOptions', 'DEFAULT_SUPPORTED_OPTIONS') }}, customizing it, and passing it to the decoder.

If you want to do this kind of check in some other context (e.g., in a [gRPC service](grpc.md) to check if you can support the options requested by the client), you can use the {{ javadoc_link_pretty('core', 'JellyOptions', 'checkCompatibility') }} method. It will throw an exception if the options are not supported.

## Useful constants

The {{ javadoc_link_pretty('core', 'JellyConstants') }} object defines some useful constants, such as the file extension for Jelly, its content type, and the version of the Jelly protocol.

## RDF Stream Taxonomy (RDF-STaX) stream type utilities

Jelly uses [RDF-STaX](https://w3id.org/stax) to define the logical stream types (more details [here]({{ proto_link('user-guide#stream-types') }})). Jelly-JVM defines each of these types as a enum in {{ javadoc_link_pretty('core', 'proto.v1.LogicalStreamType') }}.

There are have a few useful methods for working with the [RDF-STaX ontology](https://w3id.org/stax/ontology):

=== "Java"

    ```java title="Java example"
    import eu.neverblink.jelly.core.*;
    import eu.neverblink.jelly.core.utils.LogicalStreamTypeUtils;
    import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
    
    // Get the RDF-STaX IRI of a stream type
    // returns "https://w3id.org/stax/ontology#flatTripleStream"
    LogicalStreamTypeUtils.getRdfStaxType(LogicalStreamType.TRIPLES);
    ```

=== "Scala"

    ```scala title="Scala example"
    import eu.neverblink.jelly.core.*
    import eu.neverblink.jelly.core.utils.LogicalStreamTypeUtils
    import eu.neverblink.jelly.core.proto.v1.LogicalStreamType
    
    // Get the RDF-STaX IRI of a stream type
    // returns "https://w3id.org/stax/ontology#flatTripleStream"
    LogicalStreamTypeUtils.getRdfStaxType(TRIPLES)
    ```

You can also obtain a [full RDF-STaX annotation](https://w3id.org/stax/dev/use-it) for your stream if you also import an RDF library interop module (e.g., `jelly-jena` or `jelly-rdf4j`):

=== "Java"

    ```java title="Java example"
    import eu.neverblink.jelly.convert.jena.*;
    import eu.neverblink.jelly.core.*;
    import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
    import org.apache.jena.graph.NodeFactory;
    import org.apache.jena.datatypes.RDFDatatype;
    import org.apache.jena.graph.Node;
    import org.apache.jena.graph.Triple;
    
    JenaConverterFactory factory = JenaConverterFactory.getInstance(); // Jena converter factory
    Node subjectNode = NodeFactory.createURI("http://example.org/subject");
    Seq<Triple> triples = LogicalStreamTypeUtils.getRdfStaxAnnotation<>(factory, LogicalStreamType.QUADS, subjectNode);
    // Returns a Seq of three triples that would look like this in Turtle:
    // <http://example.org/subject> stax:hasStreamTypeUsage [
    //   a stax:RdfStreamTypeUsage ;
    //   stax:hasStreamType stax:flatQuadStream
    // ] .
    ```

=== "Scala"

    ```scala title="Scala example"
    import eu.neverblink.jelly.convert.jena.*
    import eu.neverblink.jelly.core.*
    import eu.neverblink.jelly.core.proto.v1.LogicalStreamType
    import org.apache.jena.graph.NodeFactory
    
    val factory = JenaConverterFactory.getInstance() // Jena converter factory
    
    val subjectNode: Node = NodeFactory.createURI("http://example.org/subject")
    val triples: Seq[Triple] = LogicalStreamTypeUtils.getRdfStaxAnnotation(factory, QUADS, subjectNode)
    // Returns a Seq of three triples that would look like this in Turtle:
    // <http://example.org/subject> stax:hasStreamTypeUsage [
    //   a stax:RdfStreamTypeUsage ;
    //   stax:hasStreamType stax:flatQuadStream
    // ] .
    ```

You can then take this annotation and expose as semantic metadata of your stream.

You can also do the opposite and construct an instance of `LogicalStreamType` from an RDF-STaX IRI:

=== "Java"

    ```java title="Java example"
    import eu.neverblink.jelly.core.LogicalStreamTypeUtils;
    import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
    
    String iri = "https://w3id.org/stax/ontology#flatQuadStream";
    // returns LogicalStreamType.QUADS
    LogicalStreamType streamType = LogicalStreamTypeUtils.fromOntologyIri(iri);
    ```

=== "Scala"

    ```scala title="Scala example"
    import eu.neverblink.jelly.core.LogicalStreamTypeUtils
    
    val iri = "https://w3id.org/stax/ontology#flatQuadStream"
    // returns LogicalStreamType.QUADS
    val streamType = LogicalStreamTypeUtils.fromOntologyIri(iri)
    ```

Finally, there are also stream type checking and manipulation utilities:

=== "Java"

    ```java title="Java example"
    import eu.neverblink.jelly.core.utils.LogicalStreamTypeUtils;
    import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
    
    // Check if this type is equal or a subtype of another type.
    // This is useful for performing compatibility checks.
    // Returns false
    LogicalStreamTypeUtils.isEqualOrSubtypeOf(LogicalStreamType.TRIPLES, LogicalStreamType.DATASETS);
    // Returns true
    LogicalStreamTypeUtils.isEqualOrSubtypeOf(LogicalStreamType.NAMED_GRAPHS, LogicalStreamType.DATASETS);
    
    // Get the "base" type of a stream type. Base types are concrete stream types
    // that have no parent types.
    // There are only 4 base types: GRAPHS, DATASETS, TRIPLES, QUADS.
    // Returns LogicalStreamType.TRIPLES
    LogicalStreamTypeUtils.toBaseType(LogicalStreamType.TRIPLES);
    // Returns LogicalStreamType.DATASETS
    LogicalStreamTypeUtils.toBaseType(LogicalStreamType.NAMED_GRAPHS);
    // Returns LogicalStreamType.DATASETS
    LogicalStreamTypeUtils.toBaseType(LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS);
    ```

=== "Scala"

    ```scala title="Scala example"
    import eu.neverblink.jelly.core.utils.LogicalStreamTypeUtils
    import eu.neverblink.jelly.core.proto.v1.LogicalStreamType
    
    // Check if this type is equal or a subtype of another type.
    // This is useful for performing compatibility checks.
    // Returns false
    LogicalStreamTypeUtils.isEqualOrSubtypeOf(LogicalStreamType.TRIPLES, LogicalStreamType.DATASETS)
    // Returns true
    LogicalStreamTypeUtils.isEqualOrSubtypeOf(LogicalStreamType.NAMED_GRAPHS, LogicalStreamType.DATASETS)
    
    // Get the "base" type of a stream type. Base types are concrete stream types 
    // that have no parent types. 
    // There are only 4 base types: GRAPHS, DATASETS, TRIPLES, QUADS.
    // Returns LogicalStreamType.TRIPLES
    LogicalStreamTypeUtils.toBaseType(LogicalStreamType.TRIPLES)
    // Returns LogicalStreamType.DATASETS
    LogicalStreamTypeUtils.toBaseType(LogicalStreamType.NAMED_GRAPHS)
    // Returns LogicalStreamType.DATASETS
    LogicalStreamTypeUtils.toBaseType(LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS)
    ```

## Jelly configuration from Typesafe config

The [`jelly-pekko-stream` module](reactive.md) also implements a utility for configuring Jelly serialization options using the [Typesafe config library](https://github.com/lightbend/config), which is commonly used in [Apache Pekko](https://pekko.apache.org/) applications.

The utility is provided by the {{ scaladoc_link_pretty('stream', 'JellyOptionsFromTypesafe$') }} object. For example:

```scala title="Scala example"
import com.typesafe.config.ConfigFactory
import eu.neverblink.jelly.stream.JellyOptionsFromTypesafe

val config = ConfigFactory.parseString("""
  |jelly.physical-type = QUADS
  |jelly.name-table-size = 1024
  |jelly.prefix-table-size = 64
  |""".stripMargin)

val options = JellyOptionsFromTypesafe.fromConfig(config.getConfig("jelly"))
options.physicalType // returns PhysicalStreamType.QUADS
options.maxNameTableSize // returns 1024
options.maxPrefixTableSize // returns 64
options.maxDatatypeTableSize // returns 16 (the default)
```

See [the source code of this class]({{ git_link('stream/src/main/scala/eu/neverblink/jelly/stream/JellyOptionsFromTypesafe.scala') }}) for more details.

## See also

- [Reactive streaming with Jelly-JVM and Apache Pekko](reactive.md)
- [Low-level usage of Jelly-JVM](low-level.md)
