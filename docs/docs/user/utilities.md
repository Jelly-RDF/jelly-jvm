# Useful utilities

This guide presents some useful utilities in the `jelly-core` and `jelly-stream` modules.

## Jelly options presets

Every Jelly stream begins with a header that specifies the serialization options used to encode the stream – [see the details in the specification]({{ proto_link('specification/serialization#stream-options') }}). So, whenever you serialize some RDF with Jelly (e.g., using [Apache Jena RIOT](jena.md), [RDF4J Rio](rdf4j.md), or the [`jelly-stream` module](reactive.md)), you need to specify these options.

The {{ javadoc_link_pretty('core', 'JellyOptions$') }} object provides a few common presets for Jelly serialization options. They return an instance of {{ javadoc_link_pretty('core', 'proto.v1.RdfStreamOptions') }} that you can further customize. For example:

```scala
import eu.ostrzyciel.jelly.core.JellyOptions

val options = JellyOptions.smallStrict

val optionsWithRdfStarSupport = JellyOptions.smallRdfStar
  
val bigWithCustomDictionarySize = JellyOptions.bigStrict
  .withMaxNameTableSize(2000)  
```

!!! warning 
    
    These presets **do not** specify the physical or logical stream type. In most cases, the Jelly library will take care of this for you and set these types automatically later. However, if you use the [low-level API](low-level.md), you need to set the stream types manually. For example:

    ```scala
    import eu.ostrzyciel.jelly.core.JellyOptions
    import eu.ostrzyciel.jelly.core.proto.v1.*

    JellyOptions.smallStrict
      .withPhysicalType(PhysicalStreamType.QUADS)
      .withLogicalType(LogicalStreamType.DATASETS)
    ```


## Checking supported options

There is also the {{ javadoc_link_pretty('core', 'JellyOptions$', 'defaultSupportedOptions') }} method which specifies the maximum set of options supported by default in Jelly-JVM, when parsing a stream. By default, Jelly-JVM will refuse to parse any stream that uses options that are beyond what is specified in this method. This is important for security reasons, as it prevents the library from, for example, allocating a 10 GB dictionary (potential Denial of Service attack).

The supported options check is carried out automatically by the decoder when parsing a stream. You cannot disable the check, but you can customize the supported options by constructing a new `RdfStreamOptions` object from {{ javadoc_link_pretty('core', 'JellyOptions$', 'defaultSupportedOptions') }}, customizing it, and passing it to the decoder.

If you want to do this kind of check in some other context (e.g., in a [gRPC service](grpc.md) to check if you can support the options requested by the client), you can use the {{ javadoc_link_pretty('core', 'JellyOptions$', 'checkCompatibility') }} method. It will throw an exception if the options are not supported.

## Useful constants

The {{ javadoc_link_pretty('core', 'Constants$') }} object defines some useful constants, such as the file extension for Jelly, its content type, and the version of the Jelly protocol.

## RDF Stream Taxonomy (RDF-STaX) stream type utilities

Jelly uses [RDF-STaX](https://w3id.org/stax) to define the logical stream types (more details [here]({{ proto_link('user-guide#stream-types') }})). Jelly-JVM defines each of these types as a case object in {{ javadoc_link_pretty('core', 'proto.v1.LogicalStreamType') }}.

These objects have a few useful methods for working with the [RDF-STaX ontology](https://w3id.org/stax/ontology):

```scala
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.LogicalStreamType

// Get the RDF-STaX IRI of a stream type
// returns "https://w3id.org/stax/ontology#flatTripleStream"
LogicalStreamType.TRIPLES.getRdfStaxType
```

You can also obtain a [full RDF-STaX annotation](https://w3id.org/stax/dev/use-it) for your stream if you also import an RDF library interop module (e.g., `jelly-jena` or `jelly-rdf4j`):

```scala
// Here we import `jena.given` to get the necessary implicit conversions.
// You can do the same with `rdf4j.given` if you are using RDF4J.
import eu.ostrzyciel.jelly.convert.jena.given
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.LogicalStreamType
import org.apache.jena.graph.NodeFactory

val subjectNode: Node = NodeFactory.createURI("http://example.org/subject")
val triples: Seq[Triple] = LogicalStreamType.QUADS.getRdfStaxAnnotation
// Returns a Seq of three triples that would look like this in Turtle:
// <http://example.org/subject> stax:hasStreamTypeUsage [
//   a stax:RdfStreamTypeUsage ;
//   stax:hasStreamType stax:flatQuadStream
// ] .
```

You can then take this annotation and expose as semantic metadata of your stream.

You can also do the opposite and construct an instance of `LogicalStreamType` from an RDF-STaX IRI:

```scala
import eu.ostrzyciel.jelly.core.LogicalStreamTypeFactory

val iri = "https://w3id.org/stax/ontology#flatQuadStream"
// returns LogicalStreamType.QUADS
val streamType = LogicalStreamTypeFactory.fromOntologyIri(iri)
```

Finally, there are also stream type checking and manipulation utilities:

```scala
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.LogicalStreamType

// Check if this type is equal or a subtype of another type.
// This is useful for performing compatibility checks.
// Returns false
LogicalStreamType.TRIPLES.isEqualOrSubtypeOf(LogicalStreamType.DATASETS)
// Returns true
LogicalStreamType.NAMED_GRAPHS.isEqualOrSubtypeOf(LogicalStreamType.DATASETS)

// Get the "base" type of a stream type. Base types are concrete stream types 
// that have no parent types. 
// There are only 4 base types: GRAPHS, DATASETS, TRIPLES, QUADS.
// Returns LogicalStreamType.TRIPLES
LogicalStreamType.TRIPLES.toBaseType
// Returns LogicalStreamType.DATASETS
LogicalStreamType.NAMED_GRAPHS.toBaseType
// Returns LogicalStreamType.DATASETS
LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS.toBaseType
```

## Jelly configuration from Typesafe config

The [`jelly-stream` module](reactive.md) also implements a utility for configuring Jelly serialization options using the [Typesafe config library](https://github.com/lightbend/config), which is commonly used in [Apache Pekko](https://pekko.apache.org/) applications.

The utility is provided by the {{ javadoc_link_pretty('stream', 'JellyOptionsFromTypesafe$') }} object. For example:

```scala
import com.typesafe.config.ConfigFactory
import eu.ostrzyciel.jelly.stream.JellyOptionsFromTypesafe

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

See [the source code of this class]({{ git_link('stream/src/main/scala/eu/ostrzyciel/jelly/stream/JellyOptionsFromTypesafe.scala') }}) for more details.

## See also

- [Reactive streaming with Jelly-JVM](reactive.md)
- [Low-level usage of Jelly-JVM](low-level.md)
