package eu.neverblink.jelly.core

import eu.neverblink.jelly.core.RdfHandler.AnyStatementHandler
import eu.neverblink.jelly.core.helpers.*
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Random

/** Tests of Jelly-RDF 1.2 (column layout): RdfEncoder and the column layout path of the decoders.
  */
class RdfColumnLayoutSpec extends AnyWordSpec, Matchers:

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
    def statements: Seq[Statement] = events.collect { case Event.Stmt(s) => s }.toSeq

  private def options(
      physicalType: PhysicalStreamType = PhysicalStreamType.TRIPLES,
      names: Int = 128,
      prefixes: Int = 16,
      datatypes: Int = 16,
  ): RdfStreamOptions.Mutable =
    RdfStreamOptions.newInstance()
      .setPhysicalType(physicalType)
      .setMaxNameTableSize(names)
      .setMaxPrefixTableSize(prefixes)
      .setMaxDatatypeTableSize(datatypes)

  /** Encoder whose frames are serialized in the sink (they are only valid there) and parsed back.
    */
  private final class Encoded(opt: RdfStreamOptions, frameSize: Int = 256):
    val frames: mutable.ListBuffer[RdfStreamFrame] = mutable.ListBuffer.empty
    val encoder: RdfEncoder[Node] = MockConverterFactory.encoder(
      RdfEncoder.Params.of(
        opt,
        frameSize,
        frame => frames += RdfStreamFrame.parseFrom(frame.toByteArray),
      ),
    )

    def decode(
        supported: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
    ): EventCollector =
      val collector = EventCollector()
      val decoder = MockConverterFactory.anyStatementDecoder(collector, supported)
      frames.foreach(decoder.ingestFrame)
      collector

  private def write(e: RdfEncoder[Node], statements: Iterable[Statement]): Unit =
    statements.foreach {
      case Triple(s, p, o) => e.handleTriple(s, p, o)
      case Quad(s, p, o, g) => e.handleQuad(s, p, o, g)
      case g: Graph => fail(s"Unexpected $g")
    }

  private val ex = "https://example.org/"
  private def iri(n: String) = Iri(ex + n)

  private val richTriples: Seq[Triple] = Seq(
    Triple(iri("s1"), iri("p1"), iri("o1")),
    Triple(iri("s1"), iri("p1"), iri("o2")),
    Triple(iri("s1"), iri("p2"), SimpleLiteral("plain")),
    Triple(iri("s1"), iri("p2"), SimpleLiteral("")),
    Triple(iri("s1"), iri("p3"), LangLiteral("hello", "en")),
    Triple(iri("s1"), iri("p3"), LangLiteral("czesc", "pl")),
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
        TripleNode(iri("a"), iri("b"), DirLangLiteral("c", "en", RdfBaseDirection.LTR)),
      ),
    ),
    Triple(
      iri("s2"),
      iri("p6"),
      TripleNode(iri("a"), iri("b"), DtLiteral("1", Datatype(ex + "int"))),
    ),
    Triple(Iri("urn:no-prefix"), Iri("urn:p"), Iri("urn:o")),
    Triple(iri("s3"), iri("p1"), iri("o1")),
    Triple(iri("s3"), iri("p1"), iri("o1")),
    Triple(iri("s3"), iri("p1"), iri("o1")),
  )

  private val richQuads: Seq[Quad] =
    richTriples.zipWithIndex.map { case (t, i) =>
      val g = i % 5 match
        case 0 => null
        case 1 => DefaultGraphNode()
        case 2 => iri("g1")
        case 3 => BlankNode("gb")
        case _ => iri("g2")
      Quad(t.s, t.p, t.o, g)
    }

  // The decoder returns the default graph node for both nulls and DefaultGraphNode
  private def normalizeGraph(q: Quad): Quad =
    if q.g == null then q.copy(g = DefaultGraphNode()) else q

  "RdfEncoder" should {
    for frameSize <- Seq(1, 2, 3, 7, 256) do
      s"round-trip triples of all term types (frame size $frameSize)" in {
        val enc = Encoded(options(), frameSize)
        write(enc.encoder, richTriples)
        enc.encoder.flush()
        enc.decode().statements shouldBe richTriples
        enc.frames.size shouldBe (richTriples.size + frameSize - 1) / frameSize
      }

      s"round-trip quads with default, named and blank node graphs (frame size $frameSize)" in {
        val enc = Encoded(options(PhysicalStreamType.QUADS), frameSize)
        write(enc.encoder, richQuads)
        enc.encoder.flush()
        enc.decode().statements shouldBe richQuads.map(normalizeGraph)
      }

    "round-trip without a prefix lookup" in {
      val enc = Encoded(options(prefixes = 0))
      write(enc.encoder, richTriples)
      enc.encoder.flush()
      enc.decode().statements shouldBe richTriples
      val column = enc.frames.head.getColumns.getSubjects
      column.getPrefixIds.size shouldBe 0
    }

    "write the options only in the first frame, with version 3 and no row layout fields" in {
      val enc = Encoded(
        JellyOptions.SMALL_ALL_FEATURES.clone()
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setLogicalType(LogicalStreamType.GRAPHS)
          .setVersion(JellyConstants.PROTO_VERSION_1_1_X),
        frameSize = 2,
      )
      write(enc.encoder, richTriples.take(5))
      enc.encoder.flush()
      enc.frames.size shouldBe 3
      enc.frames.head.getRows.size shouldBe 1
      val opt = enc.frames.head.getRows.asScala.head.getOptions
      opt.getVersion shouldBe JellyConstants.PROTO_VERSION_1_2_X
      opt.getGeneralizedStatements shouldBe false
      opt.getRdfStar shouldBe false
      opt.getLogicalType shouldBe LogicalStreamType.UNSPECIFIED
      opt.getStreamType shouldBe RdfStreamType.FLAT
      opt.getRdfVersion shouldBe RdfVersion.RDF_VERSION_UNSPECIFIED
      enc.frames.tail.foreach(_.getRows.size shouldBe 0)
      enc.encoder.getOptions shouldBe opt
    }

    "write nothing for an empty stream" in {
      val enc = Encoded(options())
      enc.encoder.flush()
      enc.frames shouldBe empty
    }

    "compress repeated terms into layout runs" in {
      val enc = Encoded(options())
      for i <- 1 to 10 do enc.encoder.handleTriple(iri("s"), iri("p"), iri(s"o$i"))
      enc.encoder.flush()
      val batch = enc.frames.head.getColumns
      batch.getRowCount shouldBe 10
      batch.getSubjects.getNameIds.size shouldBe 1
      batch.getPredicates.getNameIds.size shouldBe 1
      batch.getObjects.getNameIds.size shouldBe 10
      batch.getObjects.getLayouts.size shouldBe 0
      batch.getObjects.getKinds.isEmpty shouldBe true
    }

    "leave out the graph column if every quad is in the default graph" in {
      val enc = Encoded(options(PhysicalStreamType.QUADS))
      enc.encoder.handleQuad(iri("s"), iri("p"), iri("o"), null)
      enc.encoder.handleQuad(iri("s"), iri("p"), iri("o2"), DefaultGraphNode())
      enc.encoder.handleTriple(iri("s"), iri("p"), iri("o3"))
      enc.encoder.flush()
      enc.frames.head.getColumns.getGraphs shouldBe null
      enc.decode().statements shouldBe Seq(
        Quad(iri("s"), iri("p"), iri("o"), DefaultGraphNode()),
        Quad(iri("s"), iri("p"), iri("o2"), DefaultGraphNode()),
        Quad(iri("s"), iri("p"), iri("o3"), DefaultGraphNode()),
      )
    }

    "end frames early when the lookups fill up, and still round-trip" in {
      val statements = (1 to 2000).map(i =>
        Triple(
          Iri(s"https://ns${i % 37}.example.org/s$i"),
          Iri(s"https://p.example.org/p${i % 300}"),
          if i % 3 == 0 then DtLiteral(i.toString, Datatype(s"https://dt.example.org/t${i % 11}"))
          else Iri(s"https://o${i % 23}.example.org/o$i"),
        ),
      )
      val enc = Encoded(options(names = 128, prefixes = 8, datatypes = 4), frameSize = 100000)
      write(enc.encoder, statements)
      enc.encoder.flush()
      enc.frames.size should be > 10
      enc.decode().statements shouldBe statements
    }

    "write namespace declarations before the statements that follow them" in {
      val enc = Encoded(options(), frameSize = 100)
      enc.encoder.handleNamespace("ex", iri(""))
      enc.encoder.handleNamespace("ex2", Iri("https://example2.org/"))
      enc.encoder.handleTriple(iri("s"), iri("p"), iri("o"))
      enc.encoder.handleNamespace("ex3", Iri("https://example3.org/"))
      enc.encoder.handleTriple(iri("s"), iri("p"), iri("o2"))
      enc.encoder.flush()
      // The declaration after a statement starts a new frame
      enc.frames.size shouldBe 2
      enc.decode().events.toSeq shouldBe Seq(
        Event.Ns("ex", iri("")),
        Event.Ns("ex2", Iri("https://example2.org/")),
        Event.Stmt(Triple(iri("s"), iri("p"), iri("o"))),
        Event.Ns("ex3", Iri("https://example3.org/")),
        Event.Stmt(Triple(iri("s"), iri("p"), iri("o2"))),
      )
    }

    "write message boundaries in a MESSAGES stream" in {
      val enc = Encoded(
        options(PhysicalStreamType.QUADS).setStreamType(RdfStreamType.MESSAGES),
        frameSize = 3,
      )
      val e = enc.encoder
      e.handleMessageEnd() // empty message at the start
      for i <- 1 to 4 do e.handleTriple(iri("s"), iri("p"), iri(s"o$i"))
      e.handleMessageEnd()
      e.handleTriple(iri("s"), iri("p"), iri("o5"))
      e.handleMessageEnd()
      e.handleMessageEnd()
      e.handleTriple(iri("s"), iri("p"), iri("o6"))
      e.flush()
      def q(i: Int) = Event.Stmt(Quad(iri("s"), iri("p"), iri(s"o$i"), DefaultGraphNode()))
      enc.decode().events.toSeq shouldBe Seq(
        Event.MessageEnd,
        q(1),
        q(2),
        q(3),
        q(4),
        Event.MessageEnd,
        q(5),
        Event.MessageEnd,
        Event.MessageEnd,
        q(6),
      )
    }

    "restate the options mid-stream" in {
      val enc = Encoded(options(), frameSize = 100)
      val e = enc.encoder
      e.handleTriple(iri("s"), iri("p"), iri("o"))
      e.restateOptions(options().setRdfVersion(RdfVersion.RDF_VERSION_1_2).setMaxNameTableSize(256))
      e.handleTriple(iri("s"), iri("p"), iri("o"))
      e.handleTriple(iri("s"), iri("p"), TripleNode(iri("s"), iri("p"), iri("o")))
      e.flush()
      enc.frames.size shouldBe 2
      val restated = enc.frames(1).getRows.asScala.head.getOptions
      restated.getRdfVersion shouldBe RdfVersion.RDF_VERSION_1_2
      restated.getMaxNameTableSize shouldBe 256
      // The lookups start over: the IRIs are defined again in the second frame
      enc.frames(1).getColumns.getNames.size should be > 0
      enc.decode().statements shouldBe Seq(
        Triple(iri("s"), iri("p"), iri("o")),
        Triple(iri("s"), iri("p"), iri("o")),
        Triple(iri("s"), iri("p"), TripleNode(iri("s"), iri("p"), iri("o"))),
      )
    }

    "replace the first options if they are restated before anything is written" in {
      val enc = Encoded(options())
      enc.encoder.restateOptions(options().setRdfVersion(RdfVersion.RDF_VERSION_1_1))
      enc.encoder.handleTriple(iri("s"), iri("p"), iri("o"))
      enc.encoder.flush()
      enc.frames.size shouldBe 1
      enc.frames.head.getRows.asScala.head.getOptions.getRdfVersion shouldBe
        RdfVersion.RDF_VERSION_1_1
    }

    "end the current message when restating the options in a MESSAGES stream" in {
      val opt = options().setStreamType(RdfStreamType.MESSAGES)
      val enc = Encoded(opt)
      enc.encoder.handleTriple(iri("s"), iri("p"), iri("o"))
      enc.encoder.restateOptions(opt)
      enc.encoder.handleTriple(iri("s"), iri("p"), iri("o"))
      enc.encoder.flush()
      enc.decode().events.toSeq shouldBe Seq(
        Event.Stmt(Triple(iri("s"), iri("p"), iri("o"))),
        Event.MessageEnd,
        Event.Stmt(Triple(iri("s"), iri("p"), iri("o"))),
      )
    }

    "not end a message when restating the options in a FLAT stream" in {
      val enc = Encoded(options())
      enc.encoder.handleTriple(iri("s"), iri("p"), iri("o"))
      enc.encoder.restateOptions(options())
      enc.encoder.handleTriple(iri("s"), iri("p"), iri("o"))
      enc.encoder.flush()
      enc.decode().events.toSeq shouldBe Seq(
        Event.Stmt(Triple(iri("s"), iri("p"), iri("o"))),
        Event.Stmt(Triple(iri("s"), iri("p"), iri("o"))),
      )
    }

    "reject a different physical type when restating the options" in {
      val enc = Encoded(options())
      intercept[RdfProtoSerializationError] {
        enc.encoder.restateOptions(options(PhysicalStreamType.QUADS))
      }.getMessage should include("must stay the same")
    }

    val rejected: Seq[(String, Statement, String)] = Seq(
      ("a literal subject", Triple(SimpleLiteral("x"), iri("p"), iri("o")), "subject"),
      (
        "a triple term subject",
        Triple(TripleNode(iri("a"), iri("b"), iri("c")), iri("p"), iri("o")),
        "subject",
      ),
      ("a blank node predicate", Triple(iri("s"), BlankNode("b"), iri("o")), "predicate"),
      ("a literal predicate", Triple(iri("s"), LangLiteral("x", "en"), iri("o")), "predicate"),
      ("a literal graph", Quad(iri("s"), iri("p"), iri("o"), SimpleLiteral("g")), "graph"),
    )
    for (name, statement, position) <- rejected do
      s"reject $name, and refuse to continue afterwards" in {
        val phys = statement match
          case _: Quad => PhysicalStreamType.QUADS
          case _ => PhysicalStreamType.TRIPLES
        val enc = Encoded(options(phys))
        val e = intercept[RdfProtoSerializationError] { write(enc.encoder, Seq(statement)) }
        e.getMessage should include(s"The $position of a statement cannot be")
        e.getMessage should include("Jelly-RDF 1.1")
        intercept[RdfProtoSerializationError] {
          enc.encoder.handleTriple(iri("s"), iri("p"), iri("o"))
        }.getMessage should include("previous statement failed")
      }

    for (position, index) <- Seq("subject", "predicate", "object").zipWithIndex do
      s"reject a null $position" in {
        val enc = Encoded(options())
        val terms = Array[Node](iri("s"), iri("p"), iri("o"))
        terms(index) = null
        intercept[RdfProtoSerializationError] {
          enc.encoder.handleTriple(terms(0), terms(1), terms(2))
        }.getMessage should include(s"The $position of a statement cannot be null")
      }

    "reject quads in a TRIPLES stream" in {
      val enc = Encoded(options())
      intercept[RdfProtoSerializationError] {
        enc.encoder.handleQuad(iri("s"), iri("p"), iri("o"), iri("g"))
      }.getMessage should include("TRIPLES")
    }

    "reject message boundaries in a FLAT stream" in {
      val enc = Encoded(options())
      intercept[RdfProtoSerializationError] {
        enc.encoder.handleMessageEnd()
      }.getMessage should include("MESSAGES")
    }

    "reject terms that the declared RDF version does not allow" in {
      val dir = Triple(iri("s"), iri("p"), DirLangLiteral("x", "en", RdfBaseDirection.LTR))
      val tt = Triple(iri("s"), iri("p"), TripleNode(iri("s"), iri("p"), iri("o")))
      for (version, statement) <- Seq(
          (RdfVersion.RDF_VERSION_1_1, dir),
          (RdfVersion.RDF_VERSION_1_1, tt),
          (RdfVersion.RDF_VERSION_1_2_BASIC, tt),
        )
      do
        val enc = Encoded(options().setRdfVersion(version))
        intercept[RdfProtoSerializationError] { write(enc.encoder, Seq(statement)) }
          .getMessage should include("The stream declares")
      // RDF 1.2 Basic does allow base directions
      val enc = Encoded(options().setRdfVersion(RdfVersion.RDF_VERSION_1_2_BASIC))
      write(enc.encoder, Seq(dir))
      enc.encoder.flush()
      enc.decode().statements shouldBe Seq(dir)
    }

    "reject options it cannot write" in {
      intercept[RdfProtoSerializationError] {
        Encoded(options(PhysicalStreamType.GRAPHS))
      }.getMessage should include("TRIPLES or QUADS")
      intercept[RdfProtoSerializationError] {
        Encoded(options(names = 127))
      }.getMessage should include("minimum is 128")
      intercept[RdfProtoSerializationError] {
        Encoded(options(), frameSize = 0)
      }.getMessage should include("frame size")
    }

    for seed <- 1 to 20 do
      s"round-trip random quads (seed $seed)" in {
        val rnd = Random(seed)
        def pick[T](xs: IndexedSeq[T]): T = xs(rnd.nextInt(xs.size))
        val resources: IndexedSeq[Node] =
          (1 to 300).map(i => Iri(s"https://ns${i % 7}.example.org/r$i")) ++
            (1 to 20).map(i => BlankNode(s"b$i"))
        val literals: IndexedSeq[Node] = (1 to 100).map(i =>
          i % 5 match
            case 0 => SimpleLiteral(s"lit$i")
            case 1 => LangLiteral(s"lit$i", if i % 2 == 0 then "en" else "de")
            case 2 => DirLangLiteral(s"lit$i", "he", RdfBaseDirection.RTL)
            case 3 => DtLiteral(i.toString, Datatype(s"https://dt.example.org/t${i % 9}"))
            case _ =>
              TripleNode(Iri(s"https://ex.org/a$i"), Iri("https://ex.org/b"), SimpleLiteral("x")),
        )
        val graphs: IndexedSeq[Node] =
          IndexedSeq(null, DefaultGraphNode(), Iri("https://g.org/1"), BlankNode("g"))
        var last: Quad = Quad(pick(resources), Iri("https://p.org/p0"), pick(resources), null)
        val statements = (1 to 1500).map { _ =>
          // Repeat terms of the previous statement often, to exercise the layout runs
          val s = if rnd.nextInt(3) > 0 then last.s else pick(resources)
          val p = if rnd.nextInt(3) > 0 then last.p else Iri(s"https://p.org/p${rnd.nextInt(40)}")
          val o = if rnd.nextInt(4) == 0 then last.o
          else if rnd.nextBoolean() then pick(resources)
          else pick(literals)
          val g = if rnd.nextInt(5) > 0 then last.g else pick(graphs)
          last = Quad(s, p, o, g)
          last
        }
        val frameSize = Seq(1, 5, 64, 1000, 5000)(seed % 5)
        val opt = options(
          PhysicalStreamType.QUADS,
          names = Seq(128, 256, 4000)(seed % 3),
          prefixes = Seq(0, 8, 64)(seed % 3),
          datatypes = Seq(16, 32)(seed % 2),
        )
        val enc = Encoded(opt, frameSize)
        write(enc.encoder, statements)
        enc.encoder.flush()
        enc.decode().statements shouldBe statements.map(normalizeGraph)
      }
  }

  "ProtoDecoder (column layout)" should {
    def frameOf(opt: RdfStreamOptions, batch: RdfColumnBatch): RdfStreamFrame =
      RdfStreamFrame.newInstance()
        .addRows(RdfStreamRow.newInstance().setOptions(opt.clone().setVersion(3)))
        .setColumns(batch)

    def iriColumn(nameIds: Int*): RdfColumn.Mutable =
      val c = RdfColumn.newInstance()
      nameIds.foreach(c.addNameIds)
      c

    def batchWithNames(rows: Int, names: String*): RdfColumnBatch.Mutable =
      val entry = RdfLookupEntryPacked.newInstance()
      names.foreach(entry.addValues)
      RdfColumnBatch.newInstance().setRowCount(rows).addNames(entry)

    def decodeError(frames: RdfStreamFrame*): String =
      val decoder = MockConverterFactory.anyStatementDecoder(
        EventCollector(),
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
      )
      intercept[RdfProtoDeserializationError] { frames.foreach(decoder.ingestFrame) }.getMessage

    "decode a hand-built batch" in {
      // s p o, s p o2: the subject and predicate repeated with a layout run
      val batch = batchWithNames(2, "s", "p", "o", "o2")
        .setSubjects(iriColumn(1).addLayouts(0)) // repeat run of 2
        .setPredicates(iriColumn(2).addLayouts(0))
        .setObjects(iriColumn(3, 0))
      val collector = EventCollector()
      MockConverterFactory.triplesDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
        .ingestFrame(frameOf(options(), batch))
      collector.statements shouldBe Seq(
        Triple(Iri("s"), Iri("p"), Iri("o")),
        Triple(Iri("s"), Iri("p"), Iri("o2")),
      )
    }

    "decode a batch larger than one decoding chunk, with runs across chunk boundaries" in {
      val rows = 5000
      val batch = batchWithNames(rows, "s", "p", "o")
        // One subject repeated for every row: a single run with an extension varint
        .setSubjects(iriColumn(1).addLayouts(15).addLayouts(rows - 2 - 15))
        .setPredicates(iriColumn(2).addLayouts(15).addLayouts(rows - 2 - 15))
        .setObjects(iriColumn(3).addLayouts(15).addLayouts(rows - 2 - 15))
      val collector = EventCollector()
      MockConverterFactory.triplesDecoder(collector, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
        .ingestFrame(frameOf(options(), batch))
      collector.statements.size shouldBe rows
      collector.statements.distinct shouldBe Seq(Triple(Iri("s"), Iri("p"), Iri("o")))
    }

    "reject an unbound cell in the subject column" in {
      val batch = batchWithNames(2, "s", "p", "o")
        .setSubjects(iriColumn(1))
        .setPredicates(iriColumn(2).addLayouts(0))
        .setObjects(iriColumn(3).addLayouts(0))
      decodeError(frameOf(options(), batch)) should include("must have a value in every row")
    }

    "reject a missing column" in {
      val batch = batchWithNames(1, "s", "p").setSubjects(iriColumn(1)).setPredicates(iriColumn(2))
      decodeError(frameOf(options(), batch)) should include("no object column")
    }

    "reject a literal in the subject column" in {
      val batch = batchWithNames(1, "p", "o")
        .setSubjects(RdfColumn.newInstance().addLexValues("x"))
        .setPredicates(iriColumn(1))
        .setObjects(iriColumn(2))
      decodeError(frameOf(options(), batch)) should include("subject column may only have")
    }

    "reject a blank node in the predicate column" in {
      val batch = batchWithNames(1, "s", "o")
        .setSubjects(iriColumn(1))
        .setPredicates(RdfColumn.newInstance().addBnodes("b"))
        .setObjects(iriColumn(2))
      decodeError(frameOf(options(), batch)) should include("predicate column may only have IRIs")
    }

    "reject a graph column in a TRIPLES stream" in {
      val batch = batchWithNames(1, "s", "p", "o")
        .setSubjects(iriColumn(1))
        .setPredicates(iriColumn(2))
        .setObjects(iriColumn(3))
        .setGraphs(iriColumn(1))
      decodeError(frameOf(options(), batch)) should include("cannot have a graph column")
    }

    "reject message boundaries in a FLAT stream" in {
      decodeError(
        frameOf(options(), RdfColumnBatch.newInstance().addMessageLengths(0)),
      ) should include("only valid in streams of type MESSAGES")
    }

    "reject message lengths that add up to more than the row count" in {
      val batch = batchWithNames(1, "s", "p", "o")
        .setSubjects(iriColumn(1))
        .setPredicates(iriColumn(2))
        .setObjects(iriColumn(3))
        .addMessageLengths(1)
        .addMessageLengths(1)
      decodeError(
        frameOf(options().setStreamType(RdfStreamType.MESSAGES), batch),
      ) should include("add up to 2")
    }

    "reject statement rows in a Jelly-RDF 1.2 stream" in {
      val frame = RdfStreamFrame.newInstance()
        .addRows(RdfStreamRow.newInstance().setOptions(options().setVersion(3)))
        .addRows(RdfStreamRow.newInstance().setTriple(RdfTriple.newInstance()))
      decodeError(frame) should include("may only have the stream options in their rows")
    }

    "reject a column batch in a Jelly-RDF 1.1 stream" in {
      val frame = RdfStreamFrame.newInstance()
        .addRows(RdfStreamRow.newInstance().setOptions(options().setVersion(2)))
        .setColumns(RdfColumnBatch.newInstance())
      decodeError(frame) should include("cannot have column batches")
    }

    "reject a column batch before the stream options" in {
      val decoder = MockConverterFactory.triplesDecoder(
        EventCollector(),
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
      )
      intercept[RdfProtoDeserializationError] {
        decoder.ingestColumns(RdfColumnBatch.newInstance())
      }.getMessage should include("Stream options were not received")
    }

    "reject restated options with another physical type" in {
      val decoder = MockConverterFactory.anyStatementDecoder(
        EventCollector(),
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
      )
      decoder.ingestFrame(frameOf(options(), RdfColumnBatch.newInstance()))
      intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(
          frameOf(options(PhysicalStreamType.QUADS), RdfColumnBatch.newInstance()),
        )
      }.getMessage should include("not TRIPLES")
    }

    val badOptions: Seq[(String, RdfStreamOptions, String)] = Seq(
      (
        "generalized statements",
        options().setGeneralizedStatements(true),
        "only valid in Jelly-RDF 1.0 and 1.1",
      ),
      ("RDF-star", options().setRdfStar(true), "only valid in Jelly-RDF 1.0 and 1.1"),
      (
        "a logical type",
        options().setLogicalType(LogicalStreamType.FLAT_TRIPLES),
        "only valid in Jelly-RDF 1.0 and 1.1",
      ),
      ("the GRAPHS physical type", options(PhysicalStreamType.GRAPHS), "TRIPLES or QUADS"),
      ("an unknown stream type", options().setStreamTypeValue(7), "Unknown stream type"),
      ("an unknown RDF version", options().setRdfVersionValue(9), "Unknown RDF version"),
      ("a small name table", options(names = 64), "minimum supported size of 128"),
    )
    for (name, opt, message) <- badOptions do
      s"reject stream options with $name" in {
        decodeError(frameOf(opt, RdfColumnBatch.newInstance())) should include(message)
      }

    "reject the column layout fields in a Jelly-RDF 1.1 stream" in {
      for opt <- Seq(
          options().setStreamType(RdfStreamType.MESSAGES),
          options().setRdfVersion(RdfVersion.RDF_VERSION_1_2),
        )
      do
        val frame = RdfStreamFrame.newInstance()
          .addRows(RdfStreamRow.newInstance().setOptions(opt.setVersion(2)))
        decodeError(frame) should include("only valid in Jelly-RDF 1.2")
    }

    "reject terms that the declared RDF version does not allow" in {
      val enc = Encoded(options())
      enc.encoder.handleTriple(iri("s"), iri("p"), TripleNode(iri("s"), iri("p"), iri("o")))
      enc.encoder.flush()
      enc.frames.head.getRows.asScala.head.getOptions
        .asInstanceOf[RdfStreamOptions.Mutable]
        .setRdfVersion(RdfVersion.RDF_VERSION_1_2_BASIC)
      decodeError(enc.frames.toSeq*) should include(
        "declares RDF 1.2 Basic, but contains triple terms",
      )
    }

    "reject terms that the reader does not support" in {
      val enc = Encoded(options())
      enc.encoder.handleTriple(iri("s"), iri("p"), DirLangLiteral("x", "en", RdfBaseDirection.LTR))
      enc.encoder.flush()
      val collector = EventCollector()
      val decoder = MockConverterFactory.anyStatementDecoder(
        collector,
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone().setRdfVersion(RdfVersion.RDF_VERSION_1_1),
      )
      intercept[RdfProtoDeserializationError] {
        enc.frames.foreach(decoder.ingestFrame)
      }.getMessage should include("only supports RDF 1.1")
    }

    "reject a stream declaring a higher RDF version than the reader supports" in {
      val decoder = MockConverterFactory.anyStatementDecoder(
        EventCollector(),
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone().setRdfVersion(RdfVersion.RDF_VERSION_1_1),
      )
      intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(
          frameOf(options().setRdfVersion(RdfVersion.RDF_VERSION_1_2), RdfColumnBatch.newInstance()),
        )
      }.getMessage should include("only supports RDF 1.1")
    }
  }
