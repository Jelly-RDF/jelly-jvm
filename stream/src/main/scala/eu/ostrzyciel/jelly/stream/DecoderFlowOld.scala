//package eu.ostrzyciel.jelly.stream
//
//import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamFrame
//import eu.ostrzyciel.jelly.core.{ConverterFactory, ProtoDecoder}
//import org.apache.pekko.NotUsed
//import org.apache.pekko.stream.scaladsl.Flow
//
///**
// * Methods for creating Pekko Streams flows for decoding protobuf into RDF statements.
// */
//object DecoderFlowOld:
//
//  /**
//   * A flow converting a stream of [[RdfStreamFrame]]s into a flat stream of RDF triple statements.
//   * Physical stream type: TRIPLES.
//   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
//   * @tparam TTriple Type of triple statements.
//   * @return Pekko Streams flow
//   */
//  def triplesToFlat[TTriple](implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
//  Flow[RdfStreamFrame, TTriple, NotUsed] =
//    flatStream(factory.triplesDecoder)
//
//  /**
//   * A flow converting a stream of [[RdfStreamFrame]]s into a stream of iterables with RDF triple statements.
//   * Each iterable in the stream corresponds to one [[RdfStreamFrame]].
//   * Physical stream type: TRIPLES.
//   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
//   * @tparam TTriple Type of triple statements.
//   * @return Pekko Streams flow
//   */
//  def triplesToGrouped[TTriple](implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
//  Flow[RdfStreamFrame, IterableOnce[TTriple], NotUsed] =
//    groupedStream(factory.triplesDecoder)
//
//  /**
//   * A flow converting a stream of [[RdfStreamFrame]]s into a flat stream of RDF quad statements.
//   * Physical stream type: QUADS.
//   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
//   * @tparam TQuad Type of quad statements.
//   * @return Pekko Streams flow
//   */
//  def quadsToFlat[TQuad](implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
//  Flow[RdfStreamFrame, TQuad, NotUsed] =
//    flatStream(factory.quadsDecoder)
//
//  /**
//   * A flow converting a stream of [[RdfStreamFrame]]s into a stream of iterables with RDF quad statements.
//   * Each iterable in the stream corresponds to one [[RdfStreamFrame]].
//   * Physical stream type: QUADS.
//   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
//   * @tparam TQuad Type of quad statements.
//   * @return Pekko Streams flow
//   */
//  def quadsToGrouped[TQuad](implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
//  Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
//    groupedStream(factory.quadsDecoder)
//
//  /**
//   * A flow converting a graph stream of [[RdfStreamFrame]]s into a flat stream of RDF quad statements.
//   * Physical stream type: GRAPHS.
//   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
//   * @tparam TQuad Type of quad statements.
//   * @return Pekko Streams flow
//   */
//  def graphsAsQuadsToFlat[TQuad](implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
//  Flow[RdfStreamFrame, TQuad, NotUsed] =
//    flatStream(factory.graphsAsQuadsDecoder)
//
//  /**
//   * A flow converting a graphs stream of [[RdfStreamFrame]]s into a stream of iterables with RDF quad statements.
//   * Each iterable in the stream corresponds to one [[RdfStreamFrame]].
//   * If you need each element in the stream to correspond to one graph, use [[graphsToFlat]] instead.
//   * Physical stream type: GRAPHS.
//   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
//   * @tparam TQuad Type of quad statements.
//   * @return Pekko Streams flow
//   */
//  def graphsAsQuadsToGrouped[TQuad](implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
//  Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
//    groupedStream(factory.graphsAsQuadsDecoder)
//
//  /**
//   * A flow converting a graphs stream of [[RdfStreamFrame]]s into a stream of tuples (graph name; triples).
//   * Each element in the stream corresponds to exactly one RDF graph.
//   * Physical stream type: GRAPHS.
//   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
//   * @tparam TNode Type of RDF nodes.
//   * @tparam TTriple Type of triple statements.
//   * @return Pekko Streams flow
//   */
//  def graphsToFlat[TNode, TTriple](implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
//  Flow[RdfStreamFrame, (TNode, Iterable[TTriple]), NotUsed] =
//    flatStream(factory.graphsDecoder)
//
//  /**
//   * A flow converting a graphs stream of [[RdfStreamFrame]]s into a stream of iterables with tuples
//   * (graph name; triples).
//   * Each iterable in the stream corresponds to one [[RdfStreamFrame]].
//   * Physical stream type: GRAPHS.
//   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
//   * @tparam TNode Type of RDF nodes.
//   * @tparam TTriple Type of triple statements.
//   * @return Pekko Streams flow
//   */
//  def graphsToGrouped[TNode, TTriple](implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
//  Flow[RdfStreamFrame, IterableOnce[(TNode, Iterable[TTriple])], NotUsed] =
//    groupedStream(factory.graphsDecoder)
//
//  /**
//   * A flow converting any Jelly stream of [[RdfStreamFrame]]s into a flat stream of RDF statements (triples or quads).
//   * The type of RDF statements is determined by the stream type.
//   * The stream must have a set stream type (UNSPECIFIED is not allowed) and the stream type must not change
//   * during the stream.
//   * Physical stream type: TRIPLES, QUADS, GRAPHS.
//   *
//   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
//   * @tparam TNode Type of RDF nodes.
//   * @tparam TTriple Type of triple statements.
//   * @tparam TQuad Type of quad statements.
//   * @return Pekko Streams flow
//   */
//  def anyToFlat[TNode, TTriple, TQuad](implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
//  Flow[RdfStreamFrame, TTriple | TQuad, NotUsed] =
//    flatStream(factory.anyStatementDecoder)
//
//
//  /**
//   * A flow converting any Jelly stream of [[RdfStreamFrame]]s into a stream of iterables with RDF statements
//   * (triples or quads). Each iterable in the stream corresponds to one [[RdfStreamFrame]].
//   * The type of RDF statements is determined by the stream type.
//   * The stream must have a set stream type (UNSPECIFIED is not allowed) and the stream type must not change
//   * during the stream.
//   * Physical stream type: TRIPLES, QUADS, GRAPHS.
//   *
//   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
//   * @tparam TNode   Type of RDF nodes.
//   * @tparam TTriple Type of triple statements.
//   * @tparam TQuad   Type of quad statements.
//   * @return Pekko Streams flow
//   */
//  def anyToGrouped[TNode, TTriple, TQuad](implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
//  Flow[RdfStreamFrame, IterableOnce[TTriple | TQuad], NotUsed] =
//    groupedStream(factory.anyStatementDecoder)
//
//  private def flatStream[TOut](decoder: ProtoDecoder[TOut]): Flow[RdfStreamFrame, TOut, NotUsed] =
//    Flow[RdfStreamFrame]
//      .mapConcat(frame => frame.rows)
//      .mapConcat(row => decoder.ingestRow(row))
//
//  private def groupedStream[TOut](decoder: ProtoDecoder[TOut]):
//  Flow[RdfStreamFrame, IterableOnce[TOut], NotUsed] =
//    Flow[RdfStreamFrame]
//      .map(frame => {
//        frame.rows.flatMap(decoder.ingestRow)
//      })