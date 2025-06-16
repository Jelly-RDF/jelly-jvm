# User guide – gRPC

This guide explains the functionalities of the `jelly-pekko-grpc` module, which implements a gRPC client and server for the [Jelly gRPC streaming protocol]({{ proto_link('specification/streaming') }}).

!!! info "Prerequisites"

    If you are unfamiliar with gRPC, we recommend you first read some introductory material on the **[gRPC website](https://grpc.io/)** or in the **[Apache Pekko gRPC documentation](https://pekko.apache.org/docs/pekko-grpc/current/index.html)**.

    The `jelly-pekko-grpc` module builds on the functionalities of `jelly-pekko-stream`, so we recommend you first read the **[Apache Pekko reactive streaming guide](reactive.md)**.

    You may also want to first skim the **[Jelly gRPC streaming protocol specification]({{ proto_link('specification/streaming') }})** to understand the protocol's structure.

As with the `jelly-pekko-stream` module, you can use `jelly-pekko-grpc` with any RDF library that has a Jelly integration, such as [Apache Jena](jena.md) (using `jelly-jena`) or [RDF4J](rdf4j.md) (using `jelly-rdf4j`). The gRPC API is generic and identical across all libraries.

## Making a gRPC server and client

`jelly-pekko-grpc` builds on the [Apache Pekko gRPC library](https://pekko.apache.org/docs/pekko-grpc/current/index.html). Jelly-JVM provides boilerplate code for setting up a gRPC server and client that can send and receive Jelly streams, as shown in the example below:

{{ code_example('PekkoGrpc.scala') }}

The classes provided in `jelly-pekko-grpc` should cover most cases, but they only serve as the boilerplate. You must yourself define the logic for handling the incoming and outgoing streams, as shown in the example above.

Of course, you can also implement the server or the client from scratch, if you want to.

## See also

- [Reactive streaming with Jelly-JVM and Apache Pekko](reactive.md)
- [Useful utilities](utilities.md)
    - [Using Typesafe config to configure Jelly](utilities.md#jelly-configuration-from-typesafe-config)
