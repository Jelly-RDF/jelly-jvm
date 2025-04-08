# Jelly-JVM – getting started for developers

*If you don't want to code anything and only use Jelly with your Apache Jena/RDF4J application, see [the dedicated guide](getting-started-plugins.md) about using Jelly-JVM as a plugin.*

This guide explains a few of the basic functionalities of Jelly-JVM and how to use them in your code. Jelly-JVM is written in Scala, but it [can be used from Java as well](#quick-start-java-titanium-rdf-api).

## Quick start – Scala & Apache Jena

Depending on your RDF library of choice (Apache Jena, RDF4J, Titanium), you should import one of the dependencies: `jelly-jena`, `jelly-rdf4j`, [`jelly-titanium-rdf-api`](user/titanium.md)[^1]. In our examples we will use Jena, so let's add this to your `build.sbt` file:

```scala title="build.sbt"
lazy val jellyVersion = "{{ jvm_package_version() }}"

libraryDependencies ++= Seq(
  "eu.ostrzyciel.jelly" %% "jelly-jena" % jellyVersion,
)
```

If you are working with Maven, add this to your `pom.xml`:

```xml
<dependency>
    <groupId>eu.ostrzyciel.jelly</groupId>
    <artifactId>jelly-jena_3</artifactId>
    <version>{{ jvm_package_version() }}</version>
</dependency>
```

Now you can serialize/deserialize Jelly data with Apache Jena. Jelly is fully integrated with Jena, so it should all just magically work. Here is a simple example of reading a `.jelly` file (in this case, a metadata file from [RiverBench](https://w3id.org/riverbench/)) with [RIOT](https://jena.apache.org/documentation/io/):

```scala title="Deserialization example (Scala 3)"
import eu.ostrzyciel.jelly.convert.jena.riot.*
import org.apache.jena.riot.RDFDataMgr

// Load an RDF graph from a Jelly file
val model = RDFDataMgr.loadModel(
  "https://w3id.org/riverbench/v/2.0.1.jelly", 
  JellyLanguage.JELLY
)
// Print the size of the model
println(s"Loaded an RDF graph with ${model.size} triples")
```

Serialization is just as easy:

```scala title="Serialization example (Scala 3)"
import eu.ostrzyciel.jelly.convert.jena.riot.*
import org.apache.jena.riot.RDFDataMgr

import java.io.FileOutputStream
import scala.util.Using

// Omitted here: creating an RDF model.
// You can use the one from the previous example.

Using.resource(new FileOutputStream("metadata.jelly")) { out =>
  // Write the model to a Jelly file
  RDFDataMgr.write(out, model, JellyLanguage.JELLY)
  println("Saved the model to metadata.jelly")
}
```

[:octicons-arrow-right-24: Use Jelly-JVM with Apache Jena](user/jena.md)

[:octicons-arrow-right-24: Use Jelly-JVM with RDF4J](user/rdf4j.md)

[:octicons-arrow-right-24: Use Jelly-JVM with Titanium RDF API](user/titanium.md)

## Quick start – Java & Titanium RDF API

If you aren't using a big RDF library like Jena or RDF4J, the simplest way to get started is to use the [Titanium RDF API](user/titanium.md):

```xml
<dependency>
    <groupId>eu.ostrzyciel.jelly</groupId>
    <artifactId>jelly-titanium-rdf-api_3</artifactId>
    <version>{{ jvm_package_version() }}</version>
</dependency>
```

You can write a Jelly file like this, using the simple `RdfQuadConsumer` interface:

```java title="Titanium writer example (Java)"
var writer = TitaniumJellyWriter.factory(outputStream);
writer.quad(subject, predicate, object, ...);
```

Where `outputStream` is a Java `OutputStream` hooked up to, for example, a file on disk.

And read it like this, pointing the reader to an `RdfQuadConsumer`:

```java title="Titanium reader example (Java)"
var reader = TitaniumJellyReader.factory();
reader.parseAll(quadConsumer, inputStream);
```

In this way, you can simply convert between Jelly, [JSON-LD](https://github.com/filip26/titanium-json-ld), [CBOR-LD](https://github.com/filip26/iridium-cbor-ld), [N-Quads](https://github.com/filip26/titanium-rdf-n-quads) and other libraries supporting the `RdfQuadConsumer` interface.

[:octicons-arrow-right-24: More on using Jelly-JVM with Titanium RDF API](user/titanium.md)


## RDF streams

Now, the real power of Jelly lies in its streaming capabilities. Not only can it stream individual RDF triples/quads (this is called [_flat streaming_](https://w3id.org/stax/dev/taxonomy/#flat-rdf-stream)), but it can also very effectively handle streams of RDF graphs or datasets. To work with streams, you need to use the `jelly-stream` module, which is based on the [Apache Pekko Streams](https://pekko.apache.org/docs/pekko/current/stream/index.html) library. So, let's update our dependencies:

```scala title="build.sbt"
lazy val jellyVersion = "{{ jvm_package_version() }}"

libraryDependencies ++= Seq(
  "eu.ostrzyciel.jelly" %% "jelly-jena" % jellyVersion,
  "eu.ostrzyciel.jelly" %% "jelly-stream" % jellyVersion,
)
```

Now, let's say we have a stream of RDF graphs – for example each graph corresponds to one set of measurements from an IoT sensor. We want to have a stream that turns these graphs into their serialized representations (byte arrays), which we can then send over the network. Here is how to do it:

```scala title="Reactive streaming example (Scala 3)"
// We need to import "jena.given" for Jena-to-Jelly conversions
import eu.ostrzyciel.jelly.convert.jena.given
import eu.ostrzyciel.jelly.convert.jena.riot.*
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.riot.RDFDataMgr
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*

import scala.concurrent.ExecutionContext

// We will need a Pekko actor system to run the streams
given actorSystem: ActorSystem = ActorSystem()
// And an execution context for the futures
given ExecutionContext = actorSystem.getDispatcher

// Load an RDF graph for testing
val model = RDFDataMgr.loadModel(
  "https://w3id.org/riverbench/v/2.0.1.jelly", 
  JellyLanguage.JELLY
)

Source.repeat(model) // Create a stream of the same model over and over
  .take(10) // Take only the first 10 elements in the stream
  .map(_.asTriples) // Convert each model to an iterable of triples
  .via(EncoderFlow.graphStream( // Encode each iterable to a Jelly stream frame
    maybeLimiter = None, // 1 RDF graph = 1 message
    JellyOptions.smallStrict, // Jelly compression settings preset
  ))
  .via(JellyIo.toBytes) // Convert the stream frames to a byte arrays
  .runForeach { bytes =>
    // Just print the length of each byte array in the stream.
    // You can also hook this up to MQTT, Kafka, etc.
    println(s"Streamed ${bytes.length} bytes")
  }
  .onComplete(_ => actorSystem.terminate())
```

Jelly will compress this stream on-the-fly, so if the data is repetitive, it will be very efficient. If you run this code, you will notice that the byte sizes for the later graphs are smaller, even though we are sending the same graph over and over again. But, even if each graph is completely different, Jelly still should be much faster than other serialization formats.

These _streams_ are very powerful, because they are reactive and asynchronous – in short, this means you can hook this up to any data source and any data sink – and you can scale it up as much as you want. If you are unfamiliar with the concept of reactive streams, we recommend you start with [this Apache Pekko Streams guide](https://pekko.apache.org/docs/pekko/current/stream/stream-flows-and-basics.html).

Jelly-JVM supports streaming serialization and deserialization of all types of streams in the [RDF Stream Taxonomy](https://w3id.org/stax/dev/taxonomy/). You can read more about the theory of this and all available stream types in the [Jelly protocol documentation]({{ proto_link('user-guide') }}).

[:octicons-arrow-right-24: Learn more about reactive streaming with Jelly-JVM](user/reactive.md)

[:octicons-arrow-right-24: Learn more about the types of streams in Jelly]({{ proto_link('user-guide') }})

## gRPC streaming

Jelly is a bit more than just a serialization format – it also defines a [gRPC](https://grpc.io/)-based straming protocol. You can use it for streaming RDF data between microservices, to build a pub/sub system, or to publish RDF data to the web.

[:octicons-arrow-right-24: Learn more about using Jelly gRPC protocol servers and clients](user/grpc.md)

## Further reading

- [Using Jelly-JVM with Apache Jena](user/jena.md)
- [Using Jelly-JVM with RDF4J](user/rdf4j.md)
- [Using Jelly-JVM with Titanium RDF API](user/titanium.md)
- [Reactive streaming with Jelly-JVM](user/reactive.md) – using the `jelly-stream` module and [Apache Pekko Streams](https://pekko.apache.org/docs/pekko/current/stream/index.html)
- [Using Jelly gRPC protocol servers and clients](user/grpc.md)
- [Other useful utilities in Jelly-JVM](user/utilities.md)
- [Low-level usage of Jelly-JVM](user/low-level.md)

## Example applications using Jelly-JVM

- [The `examples` directory in the Jelly-JVM repo]({{ git_link('examples/src') }}) contains code snippets that demonstrate how to use the library in various scenarios.
- [`jelly-cli` command-line utility](https://github.com/Jelly-RDF/cli) can help you convert to/from Jelly, as well as validate and debug Jelly files.
- Nanopub [Registry](https://github.com/knowledgepixels/nanopub-registry) and [Query](https://github.com/knowledgepixels/nanopub-query) are production applications of Jelly. They use Jelly-JVM for inter-service communication, using the RDF4J integration.
- [RiverBench ci-worker](https://github.com/RiverBench/ci-worker) – a real-world application that is used for processing large RDF datasets in a CI/CD pipeline. It uses Jelly-JVM for serialization and deserialization with Apache Jena. It also uses extensively Apache Pekko Streams.
- [Jelly JVM benchmarks](https://github.com/Jelly-RDF/jvm-benchmarks/tree/main) – research software for testing the performance of Jelly-JVM and other RDF serializations in Apache Jena. It uses most of Jelly-JVM's features.


[^1]: There is nothing stopping you from using more than one at the same time. You can also pretty easily add support for any other Java-based RDF library by implementing a few interfaces. [More details here](dev/implementing.md).

## Questions?

If you have any questions about using Jelly-JVM, feel free to [open an issue on GitHub](https://github.com/Jelly-RDF/jelly-jvm/issues/new/choose).
