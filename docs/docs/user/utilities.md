# Useful utilities

This guide presents some useful utilities in the `jelly-core` and `jelly-pekko-stream` modules.

## Jelly options presets

Every Jelly stream begins with a header that specifies the serialization options used to encode the stream – [see the details in the specification]({{ proto_link('specification/serialization#stream-options') }}). So, whenever you serialize some RDF with Jelly (e.g., using [Apache Jena RIOT](jena.md), [RDF4J Rio](rdf4j.md), or the [`jelly-pekko-stream` module](reactive.md)), you need to specify these options.

The {{ javadoc_link_pretty('core', 'JellyOptions') }} object provides a few common presets for Jelly serialization options. They return an instance of {{ javadoc_link_pretty('core', 'proto.v1.RdfStreamOptions') }} that you can further customize. For example:

=== "Java"

    ```java title="Java example"
    import eu.neverblink.jelly.core.JellyOptions;
    
    RdfStreamOptions options = JellyOptions.SMALL_STRICT;
    
    RdfStreamOptions bigWithCustomDictionarySize = JellyOptions.BIG_STRICT
      .clone()
      .setMaxNameTableSize(2000);
    ```

=== "Scala"

    ```scala title="Scala example"
    import eu.neverblink.jelly.core.JellyOptions
    
    val options = JellyOptions.SMALL_STRICT
    
    val bigWithCustomDictionarySize = JellyOptions.BIG_STRICT
      .clone()
      .setMaxNameTableSize(2000)  
    ```

The `GENERALIZED`, `RDF_STAR` and `ALL_FEATURES` presets differ from the `STRICT` ones only in the generalized RDF and RDF-star flags, which apply to Jelly-RDF 1.0 and 1.1 streams and to Jelly-Patch. Jelly-RDF 1.2 output ignores them.

!!! warning 
    
    These presets **do not** specify the physical type or the stream type. In most cases, the Jelly library will take care of this for you and set these types automatically later. However, if you use the [low-level API](low-level.md), you need to set the stream types manually. For example:

    === "Java"

        ```java title="Java example"
        import eu.neverblink.jelly.core.JellyOptions;
        import eu.neverblink.jelly.core.proto.v1.*;
    
        JellyOptions.SMALL_STRICT
          .clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setStreamType(RdfStreamType.MESSAGES);
        ```

    === "Scala"

        ```scala title="Scala example"
        import eu.neverblink.jelly.core.JellyOptions
        import eu.neverblink.jelly.core.proto.v1.*
    
        JellyOptions.SMALL_STRICT
          .clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setStreamType(RdfStreamType.MESSAGES)
        ```

## Checking supported options

There is also the {{ javadoc_link_pretty('core', 'JellyOptions', 'DEFAULT_SUPPORTED_OPTIONS') }} field which specifies the maximum set of options supported by default in Jelly-JVM, when parsing a stream. By default, Jelly-JVM will refuse to parse any stream that uses options that are beyond what is specified in this method. This is important for security reasons, as it prevents the library from, for example, allocating a 10 GB dictionary (potential Denial of Service attack).

The supported options check is carried out automatically by the decoder when parsing a stream. You cannot disable the check, but you can customize the supported options by constructing a new `RdfStreamOptions` object from {{ javadoc_link_pretty('core', 'JellyOptions', 'DEFAULT_SUPPORTED_OPTIONS') }}, customizing it, and passing it to the decoder.

If you want to do this kind of check in some other context (e.g., in a [gRPC service](grpc.md) to check if you can support the options requested by the client), you can use the {{ javadoc_link_pretty('core', 'JellyOptions', 'checkCompatibility') }} method. It will throw an exception if the options are not supported.

## Useful constants

The {{ javadoc_link_pretty('core', 'JellyConstants') }} object defines some useful constants, such as the file extension for Jelly, its content type, and the version of the Jelly protocol.

## Jelly configuration from Typesafe config

The [`jelly-pekko-stream` module](reactive.md) also implements a utility for configuring Jelly serialization options using the [Typesafe config library](https://github.com/lightbend/config), which is commonly used in [Apache Pekko](https://pekko.apache.org/) applications.

The utility is provided by the {{ scaladoc_link_pretty('pekko-stream', 'JellyOptionsFromTypesafe$') }} object. For example:

```scala title="Scala example"
import com.typesafe.config.ConfigFactory
import eu.neverblink.jelly.pekko.stream.JellyOptionsFromTypesafe

val config = ConfigFactory.parseString("""
  |jelly.physical-type = QUADS
  |jelly.rdf-version = "1.2-basic"
  |jelly.name-table-size = 1024
  |jelly.prefix-table-size = 64
  |""".stripMargin)

val options = JellyOptionsFromTypesafe.fromTypesafeConfig(config.getConfig("jelly"))
options.getPhysicalType // returns PhysicalStreamType.QUADS
options.getRdfVersion // returns RdfVersion.RDF_VERSION_1_2_BASIC
options.getMaxNameTableSize // returns 1024
options.getMaxPrefixTableSize // returns 64
options.getMaxDatatypeTableSize // returns 16 (the default)
```

The keys `stream-type` (`FLAT` or `MESSAGES`) and `rdf-version` (`"1.1"`, `"1.2-basic"`, `"1.2"`, or `""` for unspecified) only apply to Jelly-RDF 1.2 streams. The keys `logical-type`, `generalized-statements`, and `rdf-star` only apply to Jelly-RDF 1.0 and 1.1 streams. The key `protocol-version` sets the version of Jelly-RDF to write: 0 (the default) means Jelly-RDF 1.2, and 1 or 2 mean Jelly-RDF 1.0 or 1.1 (deprecated).

See [the source code of this class]({{ git_link('pekko-stream/src/main/scala/eu/neverblink/jelly/pekko/stream/JellyOptionsFromTypesafe.scala') }}) for more details.

## See also

- [Reactive streaming with Jelly-JVM and Apache Pekko](reactive.md)
- [Low-level usage of Jelly-JVM](low-level.md)
