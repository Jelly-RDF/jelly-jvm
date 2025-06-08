# Developer guide – implementing conversions for other libraries

## Basics of implementing conversions
Currently converters for the two most popular RDF JVM libraries are implemented – RDF4J and Jena. But it is possible to implement your own converters and adapt the Jelly serialization code to any RDF library with little effort.

To do this, you will need to implement three interfaces from the `jelly-core` module: `ProtoEncoderConverter`, `ProtoDecoderConverter`, and `JellyConverterFactory`.

- **ProtoEncoderConverter (serialization)**
    - `nodeToProto` and `graphToProto` should translate into Jelly's representation all possible variations of RDF terms in the SPO and G positions, respectively.
    - Example implementation for Jena: [JenaEncoderConverter]({{ git_link('jena/src/main/java/eu/neverblink/jelly/convert/jena/JenaEncoderConverter.java') }})
    - You can skip implementing this interface if you don't need serialization.
    - You can also skip implementing some methods (make them throw an exception or return null) if, for example, you don't want to work with quads.

- **ProtoDecoderConverter (deserialization)**
    - The `make*` methods should construct new RDF terms and statements.
    - Example implementation for Jena: [JenaDecoderConverter]({{ git_link('jena/src/main/java/eu/neverblink/jelly/convert/jena/JenaDecoderConverter.java') }})
    - You can skip implementing this interface if you don't need deserialization.
    - You can also skip implementing some methods (make them throw an exception or return null) if, for example, you don't want to work with quads or RDF-star.

- **JellyConverterFactory** – wrapper that allows other modules to use your converter.
    - The methods should just return new instances (or singletons, if appropriate for your use case) of your `ProtoEncoderConverter` and `ProtoDecoderConverter` implementations.
    - Example for Jena: [JenaConverterFactory]({{ git_link('jena/src/main/java/eu/neverblink/jelly/convert/jena/JenaConverterFactory.java') }})


## Supporting reactive streams with Apache Pekko Streams

If you want to enable reactive stream support for your library, it will be useful to implement the following utility interfaces from `jelly-core`: `QuadExtractor` and `QuadMaker` for Quads and `TripleExtractor` and `TripleMaker` for Triples. 

These interfaces are used to extract triples/quads from the input stream and create them from the output stream. In `jelly-pekko-stream` module we expect your `ProtoDecoderConverter` to implement `TripleMaker` and `QuadMaker` interfaces, and your `ProtoEncoderConverter` to implement `TripleExtractor` and `QuadExtractor` interfaces. 

The implementation of these interfaces is very simple and should be similar to the following examples:

- [JenaEncoderConverter]({{ git_link('jena/src/main/java/eu/neverblink/jelly/convert/jena/JenaEncoderConverter.java') }}) which implements `TripleExtractor<Node, Triple>` and `QuadExtractor<Node, Quad>` interfaces.
- [JenaDecoderConverter]({{ git_link('jena/src/main/java/eu/neverblink/jelly/convert/jena/JenaDecoderConverter.java') }}) which implements `TripleMaker<Node, Triple>` and `QuadMaker<Node, Quad>` interfaces.

Additionally, to support `RdfSourceBuilder` Dataset/Graph to Triples, Quads and Graphs conversions, you may find it useful to create implementations of `DatasetAdapter` and `GraphAdapter` utilis interfaces located in `jelly-core`. A good example of implementing such interfaces can be found in the `jena` module: [JenaAdapters]({{ git_link('jena/src/main/java/eu/neverblink/jelly/convert/jena/JenaAdapters.java') }}).
