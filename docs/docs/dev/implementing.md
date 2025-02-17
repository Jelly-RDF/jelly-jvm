# Developer guide – implementing conversions for other libraries

Currently converters for the two most popular RDF JVM libraries are implemented – RDF4J and Jena. But it is possible to implement your own converters and adapt the Jelly serialization code to any RDF library with little effort.

To do this, you will need to implement three traits (interfaces in Java) from the `jelly-core` module: `ProtoEncoderConverter`, `ProtoDecoderConverter`, and `ConverterFactory`.

- **ProtoEncoderConverter (serialization)**
    - `get*` methods deconstruct triple and quad statements.
    - `nodeToProto` and `graphToProto` should translate into Jelly's representation all possible variations of RDF terms in the SPO and G positions, respectively.
    - Example implementation for Jena: [JenaEncoderConverter]({{ git_link('jena/src/main/scala/eu/ostrzyciel/jelly/convert/jena/JenaEncoderConverter.scala') }})
    - You can skip implementing this trait if you don't need serialization.
    - You can also skip implementing some methods (make them throw an exception or return null) if, for example, you don't want to work with quads.

- **ProtoDecoderConverter (deserialization)**
    - The `make*` methods should construct new RDF terms and statements. You can make them `inline`.
    - Example implementation for Jena: [JenaDecoderConverter]({{ git_link('jena/src/main/scala/eu/ostrzyciel/jelly/convert/jena/JenaDecoderConverter.scala') }})
    - You can skip implementing this trait if you don't need deserialization.
    - You can also skip implementing some methods (make them throw an exception or return null) if, for example, you don't want to work with quads or RDF-star.

- **ConverterFactory** – wrapper that allows other modules to use your converter.
    - The methods should just return new instances (or singletons, if appropriate for your use case) of your `ProtoEncoderConverter` and `ProtoDecoderConverter` implementations.
    - Example for Jena: [JenaConverterFactory]({{ git_link('jena/src/main/scala/eu/ostrzyciel/jelly/convert/jena/JenaConverterFactory.scala') }})
