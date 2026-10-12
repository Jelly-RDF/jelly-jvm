package eu.neverblink.jelly.core

import com.google.protobuf.ByteString
import eu.neverblink.jelly.core.RdfHandler.AnyStatementHandler
import eu.neverblink.jelly.core.helpers.*
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Random

/** Unit tests for the ProtoTranscoder class. See also integration tests:
  * [[eu.ostrzyciel.jelly.integration_tests.CrossTranscodingSpec]]
  */
class ProtoTranscoderSpec extends AnyWordSpec, Matchers:

  /** What a decoder hands to its handler, in order. */
  enum Event:
    case Stmt(statement: Statement)
    case Ns(prefix: String, iri: Node)
    case MessageEnd

  final class EventCollector extends AnyStatementHandler[Node]:
    val events: mutable.ListBuffer[Event] = mutable.ListBuffer.empty
    override def handleNamespace(prefix: String, namespace: Node): Unit =
      events += Event.Ns(prefix, namespace)
    override def handleTriple(subject: Node, predicate: Node, `object`: Node): Unit =
      events += Event.Stmt(Triple(subject, predicate, `object`))
    override def handleQuad(subject: Node, predicate: Node, `object`: Node, graph: Node): Unit =
      events += Event.Stmt(Quad(subject, predicate, `object`, graph))
    override def handleMessageEnd(): Unit = events += Event.MessageEnd

  private def options(
      physicalType: PhysicalStreamType = PhysicalStreamType.TRIPLES,
      names: Int = 128,
      prefixes: Int = 16,
      datatypes: Int = 16,
      streamType: RdfStreamType = RdfStreamType.FLAT,
  ): RdfStreamOptions.Mutable =
    RdfStreamOptions.newInstance()
      .setPhysicalType(physicalType)
      .setStreamType(streamType)
      .setMaxNameTableSize(names)
      .setMaxPrefixTableSize(prefixes)
      .setMaxDatatypeTableSize(datatypes)

  /** Encodes statements into Jelly-RDF 1.2 frames, serialized and parsed back. */
  private def encode(
      opt: RdfStreamOptions,
      statements: Iterable[Statement | NamespaceDeclaration | Event],
      frameSize: Int = 256,
  ): Seq[RdfStreamFrame] =
    val frames = mutable.ListBuffer.empty[RdfStreamFrame]
    val encoder: RdfEncoder[Node] = MockConverterFactory.encoder(
      RdfEncoder.Params.of(opt, frameSize, f => frames += RdfStreamFrame.parseFrom(f.toByteArray)),
    )
    statements.foreach {
      case Triple(s, p, o) => encoder.handleTriple(s, p, o)
      case Quad(s, p, o, g) => encoder.handleQuad(s, p, o, g)
      case ns: NamespaceDeclaration => encoder.handleNamespace(ns.prefix, Iri(ns.iri))
      case Event.MessageEnd => encoder.handleMessageEnd()
      case other => fail(s"Unexpected $other")
    }
    encoder.flush()
    frames.toSeq

  private def decode(frames: Iterable[RdfStreamFrame]): Seq[Event] =
    val collector = EventCollector()
    val decoder =
      MockConverterFactory.anyStatementDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
    frames.foreach(decoder.ingestFrame)
    collector.events.toSeq

  /** Transcodes frames, and serializes and parses the output back, to check it is well-formed. */
  private def transcode(t: ProtoTranscoder, frames: Iterable[RdfStreamFrame]): Seq[RdfStreamFrame] =
    frames.map(f => RdfStreamFrame.parseFrom(t.ingestFrame(f).toByteArray)).toSeq

  private def lookupEntries(frames: Iterable[RdfStreamFrame]): Int =
    frames.flatMap(f => Option(f.getColumns)).map { b =>
      (b.getNames.asScala ++ b.getPrefixes.asScala ++ b.getDatatypes.asScala)
        .map(_.getValues.size).sum
    }.sum

  private val ex = "https://example.org/"
  private def iri(n: String) = Iri(ex + n)

  private val richTriples: Seq[Triple] = Seq(
    Triple(iri("s1"), iri("p1"), iri("o1")),
    Triple(iri("s1"), iri("p1"), iri("o2")),
    Triple(iri("s1"), iri("p2"), SimpleLiteral("plain")),
    Triple(iri("s1"), iri("p3"), LangLiteral("hello", "en")),
    Triple(BlankNode("b1"), iri("p3"), DirLangLiteral("مرحبا", "ar", RdfBaseDirection.RTL)),
    Triple(BlankNode("b1"), iri("p4"), DtLiteral("42", Datatype(ex + "int"))),
    Triple(BlankNode("b1"), iri("p4"), DtLiteral("4.2", Datatype(ex + "double"))),
    Triple(BlankNode("b2"), iri("p5"), BlankNode("b1")),
    Triple(iri("s2"), iri("p6"), TripleNode(iri("a"), iri("b"), SimpleLiteral("c"))),
    Triple(
      iri("s2"),
      iri("p6"),
      TripleNode(
        BlankNode("x"),
        iri("b"),
        TripleNode(iri("a"), Iri("urn:b"), DtLiteral("1", Datatype(ex + "int"))),
      ),
    ),
    Triple(Iri("urn:no-prefix"), Iri("urn:p"), Iri("urn:o")),
    Triple(iri("s3"), iri("p1"), iri("o1")),
    Triple(iri("s3"), iri("p1"), iri("o1")),
  )

  private val richQuads: Seq[Quad] =
    richTriples.zipWithIndex.map { case (t, i) =>
      val g = i % 4 match
        case 0 => DefaultGraphNode()
        case 1 => iri("g1")
        case 2 => BlankNode("gb")
        case _ => iri("g2")
      Quad(t.s, t.p, t.o, g)
    }

  /** Random statements over more IRIs, prefixes and datatypes than small lookup tables hold. A
    * statement has at most 9 IRIs, so it needs at most 9 entries of the prefix table.
    */
  private def randomStatements(rnd: Random, n: Int, quads: Boolean): Seq[Statement] =
    def namedIri(): Node =
      if rnd.nextInt(10) == 0 then Iri(s"urn:x${rnd.nextInt(50)}")
      else Iri(s"https://ex${rnd.nextInt(12)}.org/ns/n${rnd.nextInt(300)}")
    def subject(): Node =
      if rnd.nextInt(6) == 0 then BlankNode(s"b${rnd.nextInt(20)}") else namedIri()
    def literal(): Node = rnd.nextInt(5) match
      case 0 => SimpleLiteral(s"v${rnd.nextInt(50)}")
      case 1 => LangLiteral("x", Seq("en", "pl", "de")(rnd.nextInt(3)))
      case 2 =>
        DirLangLiteral(
          "y",
          "ar",
          if rnd.nextBoolean() then RdfBaseDirection.LTR else RdfBaseDirection.RTL,
        )
      case _ => DtLiteral(s"${rnd.nextInt(100)}", Datatype(s"https://dt.org/t${rnd.nextInt(12)}"))
    def obj(depth: Int): Node = rnd.nextInt(10) match
      case 0 | 1 | 2 => literal()
      case 3 => BlankNode(s"b${rnd.nextInt(20)}")
      case 4 if depth < 2 => TripleNode(subject(), namedIri(), obj(depth + 1))
      case _ => namedIri()
    def graph(): Node = rnd.nextInt(4) match
      case 0 => DefaultGraphNode()
      case 1 => BlankNode(s"g${rnd.nextInt(5)}")
      case _ => namedIri()
    // Runs of the same subject, as in most real data
    var s = subject()
    for _ <- 0 until n yield
      if rnd.nextInt(4) == 0 then s = subject()
      if quads then Quad(s, namedIri(), obj(0), graph()) else Triple(s, namedIri(), obj(0))

  "ProtoTranscoder" should {
    "write a single stream as it was when the output has the same options" when {
      for (name, physicalType, statements) <- Seq(
          ("triples", PhysicalStreamType.TRIPLES, richTriples),
          ("quads", PhysicalStreamType.QUADS, richQuads),
        )
      do
        s"input is $name" in {
          val opt = options(physicalType)
          val input = encode(opt, statements, frameSize = 5)
          val output = transcode(JellyTranscoderFactory.fastMergingTranscoderUnsafe(opt), input)
          output shouldBe input
        }
    }

    "write a big single stream as it was when the output has the same options" in {
      val opt = options(prefixes = 10, datatypes = 8)
      val input = encode(opt, randomStatements(Random(1), 3000, quads = false), frameSize = 100)
      val output = transcode(JellyTranscoderFactory.fastMergingTranscoderUnsafe(opt), input)
      output shouldBe input
    }

    "not repeat lookup entries when the same stream comes again" in {
      val opt = options(PhysicalStreamType.QUADS)
      val input = encode(opt, richQuads, frameSize = 4)
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(opt)
      val first = transcode(t, input)
      val second = transcode(t, input)
      val third = transcode(t, input)
      lookupEntries(first) shouldBe lookupEntries(input)
      lookupEntries(second) shouldBe 0
      lookupEntries(third) shouldBe 0
      second.head.getRows.size shouldBe 0
      decode(first ++ second ++ third) shouldBe decode(input ++ input ++ input)
    }

    "merge random streams into one" when {
      for seed <- 1 to 20 do
        s"random seed is $seed" in {
          val rnd = Random(seed)
          val quads = seed % 2 == 0
          val physicalType = if quads then PhysicalStreamType.QUADS else PhysicalStreamType.TRIPLES
          // Small tables, so that the lookups evict all the time
          val output = options(
            physicalType,
            names = 128 + rnd.nextInt(3) * 64,
            prefixes = 10 + rnd.nextInt(7),
            datatypes = 4 + rnd.nextInt(8),
          )
          val inputs = for _ <- 1 to 2 + rnd.nextInt(6) yield
            val opt = options(
              physicalType,
              names = 128 + rnd.nextInt((output.getMaxNameTableSize - 128) + 1),
              prefixes = 10 + rnd.nextInt(output.getMaxPrefixTableSize - 9),
              datatypes = 1 + rnd.nextInt(output.getMaxDatatypeTableSize),
            )
            encode(opt, randomStatements(rnd, 1 + rnd.nextInt(1500), quads), 1 + rnd.nextInt(300))
          val t =
            if rnd.nextBoolean() then JellyTranscoderFactory.fastMergingTranscoderUnsafe(output)
            else
              JellyTranscoderFactory.fastMergingTranscoder(
                JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
                output,
              )
          val result = inputs.flatMap(transcode(t, _))
          decode(result) shouldBe decode(inputs.flatten)
          // Only the first frame has the options
          result.head.getRows.size shouldBe 1
          result.tail.foreach(_.getRows.size shouldBe 0)
        }
    }

    "merge streams without a prefix table into one with one" in {
      val rnd = Random(7)
      val inputs =
        for i <- 1 to 3
        yield encode(
          options(prefixes = if i == 2 then 12 else 0),
          randomStatements(rnd, 500, quads = false),
          64,
        )
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(options(prefixes = 12))
      val result = inputs.flatMap(transcode(t, _))
      decode(result) shouldBe decode(inputs.flatten)
    }

    "transcode a stream that restates its options" in {
      val opt = options(prefixes = 10, datatypes = 4)
      val frames = mutable.ListBuffer.empty[RdfStreamFrame]
      val encoder: RdfEncoder[Node] = MockConverterFactory.encoder(
        RdfEncoder.Params.of(opt, 50, f => frames += RdfStreamFrame.parseFrom(f.toByteArray)),
      )
      val rnd = Random(3)
      for (st, i) <- randomStatements(rnd, 600, quads = false).zipWithIndex do
        if i % 200 == 199 then
          encoder.restateOptions(opt.clone().setMaxDatatypeTableSize(2 + i / 200))
        val Triple(s, p, o) = st.asInstanceOf[Triple]
        encoder.handleTriple(s, p, o)
      encoder.flush()
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(opt)
      decode(transcode(t, frames)) shouldBe decode(frames)
    }

    "transcode namespace declarations" in {
      val input = Seq(
        NamespaceDeclaration("ex", ex),
        NamespaceDeclaration("other", "https://other.org/"),
        richTriples.head,
        NamespaceDeclaration("ex2", ex + "ns2/"),
      ) ++ richTriples.tail
      val frames = encode(options(), input, frameSize = 3)
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(options())
      // Twice: the second time, the IRIs of the declarations refer to entries set earlier
      val result = transcode(t, frames) ++ transcode(t, frames)
      decode(result) shouldBe decode(frames ++ frames)
      decode(result).collect { case e: Event.Ns => e }.size shouldBe 6
    }

    "keep the message boundaries of streams of RDF Messages" in {
      val opt = options(streamType = RdfStreamType.MESSAGES)
      val (a, b) = richTriples.splitAt(5)
      // Stream 1 ends in the middle of a message, stream 2 starts with an empty message
      val stream1 = encode(opt, a.take(2) ++ Seq(Event.MessageEnd) ++ a.drop(2), frameSize = 2)
      val stream2 = encode(opt, Seq(Event.MessageEnd) ++ b ++ Seq(Event.MessageEnd), frameSize = 3)
      val stream3 = encode(opt, a, frameSize = 100)
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(opt)
      val result = transcode(t, stream1) ++ transcode(t, stream2) ++ transcode(t, stream3)
      val events = decode(result)
      events shouldBe decode(stream1 ++ stream2 ++ stream3)
      events.count(_ == Event.MessageEnd) shouldBe 4
    }

    "end the message of the output in a frame with only the options" in {
      val opt = options(streamType = RdfStreamType.MESSAGES)
      val stream1 = encode(opt, richTriples.take(3))
      val optionsOnly = RdfStreamFrame.newInstance()
        .addRows(
          RdfStreamRow.newInstance().setOptions(stream1.head.getRows.asScala.head.getOptions),
        )
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(opt)
      val result = transcode(t, stream1 ++ Seq(optionsOnly))
      result.last.getColumns.getRowCount shouldBe 0
      result.last.getColumns.getMessageLengths.size shouldBe 1
      decode(result).last shouldBe Event.MessageEnd
    }

    "transcode an empty frame" in {
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(options())
      t.ingestFrame(RdfStreamFrame.EMPTY) shouldBe RdfStreamFrame.EMPTY
    }

    "keep the metadata of the frames" in {
      val input = encode(options(), richTriples, frameSize = 4)
      val withMetadata = input.zipWithIndex.map { (f, i) =>
        val m = f.clone()
        if i % 2 == 0 then
          m.addMetadata(
            RdfStreamFrame.MetadataEntry.newInstance()
              .setKey(s"key$i")
              .setValue(ByteString.copyFromUtf8(s"value$i")),
          )
        m
      }
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(options())
      val result = transcode(t, withMetadata)
      result.map(_.getMetadata.asScala.toSeq) shouldBe withMetadata.map(_.getMetadata.asScala.toSeq)
    }

    "write the options of the output, in the column layout" in {
      val output = options(names = 256, prefixes = 32, datatypes = 32)
        .setVersion(JellyConstants.PROTO_VERSION_1_2_X)
        .setStreamName("merged")
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(output)
      val result = transcode(t, encode(options(), richTriples))
      val written = result.head.getRows.asScala.head.getOptions
      written shouldBe output
    }

    "refuse to write anything but Jelly-RDF 1.2" in {
      for version <- Seq(JellyConstants.PROTO_VERSION_1_0_X, JellyConstants.PROTO_VERSION_1_1_X) do
        an[IllegalArgumentException] should be thrownBy
          JellyTranscoderFactory.fastMergingTranscoderUnsafe(options().setVersion(version))
      an[IllegalArgumentException] should be thrownBy
        JellyTranscoderFactory.fastMergingTranscoderUnsafe(options(PhysicalStreamType.GRAPHS))
    }

    "refuse row layout input" in {
      val rowFrame = RdfStreamFrame.newInstance().addRows(
        RdfStreamRow.newInstance().setOptions(
          options().setVersion(JellyConstants.PROTO_VERSION_1_1_X),
        ),
      )
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(options())
      val e = intercept[RdfProtoTranscodingError](t.ingestFrame(rowFrame))
      e.getMessage should include("only supports Jelly-RDF 1.2")
    }

    "refuse rows other than the options" in {
      val frame = RdfStreamFrame.newInstance()
        .addRows(RdfStreamRow.newInstance().setOptions(options().setVersion(3)))
        .addRows(RdfStreamRow.newInstance().setName(RdfNameEntry.newInstance().setValue("x")))
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(options())
      an[RdfProtoTranscodingError] should be thrownBy t.ingestFrame(frame)
    }

    "refuse a batch before the options" in {
      val frames = encode(options(), richTriples)
      val withoutOptions = frames.head.clone().setRows(
        RdfStreamFrame.newInstance().getRows,
      )
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(options())
      val e = intercept[RdfProtoTranscodingError](t.ingestFrame(withoutOptions))
      e.getMessage should include("no stream options")
    }

    "refuse input of another physical type or stream type" in {
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(options())
      intercept[RdfProtoTranscodingError](
        t.ingestFrame(encode(options(PhysicalStreamType.QUADS), richQuads).head),
      ).getMessage should include("physical type")
      intercept[RdfProtoTranscodingError](
        t.ingestFrame(encode(options(streamType = RdfStreamType.MESSAGES), richTriples).head),
      ).getMessage should include("stream type")
    }

    "refuse input with larger lookup tables than the output" in {
      for (name, input) <- Seq(
          ("name", options(names = 256)),
          ("prefix", options(prefixes = 17)),
          ("datatype", options(datatypes = 17)),
        )
      do
        val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(options())
        val e = intercept[RdfProtoTranscodingError](t.ingestFrame(encode(input, richTriples).head))
        e.getMessage should include(s"its $name table")
    }

    "check the input options against the supported ones" in {
      val supported = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone().setMaxPrefixTableSize(4)
      val t = JellyTranscoderFactory.fastMergingTranscoder(supported, options())
      an[RdfProtoDeserializationError] should be thrownBy
        t.ingestFrame(encode(options(prefixes = 8), richTriples).head)
    }

    "refuse terms that the output's RDF version does not allow" in {
      val tripleTerm = Seq(Triple(iri("s"), iri("p"), TripleNode(iri("a"), iri("b"), iri("c"))))
      val direction =
        Seq(Triple(iri("s"), iri("p"), DirLangLiteral("a", "en", RdfBaseDirection.LTR)))
      val nestedDirection = Seq(
        Triple(
          iri("s"),
          iri("p"),
          TripleNode(iri("a"), iri("b"), DirLangLiteral("a", "en", RdfBaseDirection.LTR)),
        ),
      )
      def run(version: RdfVersion, input: Seq[Statement]) =
        val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(options().setRdfVersion(version))
        transcode(t, encode(options(), input))

      intercept[RdfProtoTranscodingError](run(RdfVersion.RDF_VERSION_1_1, tripleTerm))
        .getMessage should include("triple terms")
      intercept[RdfProtoTranscodingError](run(RdfVersion.RDF_VERSION_1_2_BASIC, tripleTerm))
        .getMessage should include("triple terms")
      intercept[RdfProtoTranscodingError](run(RdfVersion.RDF_VERSION_1_1, direction))
        .getMessage should include("base directions")
      run(RdfVersion.RDF_VERSION_1_2_BASIC, direction)
      run(RdfVersion.RDF_VERSION_1_2, nestedDirection)
      run(RdfVersion.RDF_VERSION_1_1, richTriples.take(4))
    }

    "refuse references to lookup ids that the input did not set" in {
      val frame = encode(options(), richTriples.take(1)).head.clone()
      frame.getColumns.asInstanceOf[RdfColumnBatch.Mutable].getNames.clear()
      val t = JellyTranscoderFactory.fastMergingTranscoderUnsafe(options())
      val e = intercept[RdfProtoTranscodingError](t.ingestFrame(frame))
      e.getMessage should include("did not set")
    }
  }
