package eu.neverblink.jelly.core.memory

import eu.neverblink.jelly.core.proto.v1.{RdfQuad, RdfTriple}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EncoderAllocatorSpec extends AnyWordSpec, Matchers:
  "EncoderAllocator -- heap" should {
    "return new messages" in {
      val allocator = EncoderAllocator.newHeapAllocator()
      allocator.newTriple() should be (a[RdfTriple.Mutable])
      allocator.newQuad() should be (a[RdfQuad.Mutable])
    }

    "not reuse messages after releaseAll()" in {
      val allocator = EncoderAllocator.newHeapAllocator()
      val t1 = allocator.newTriple()
      val q1 = allocator.newQuad()
      allocator.releaseAll()
      allocator.newTriple() should not be theSameInstanceAs (t1)
      allocator.newQuad() should not be theSameInstanceAs (q1)
    }
  }

  "EncoderAllocator -- arena" should {
    "return unique messages" in {
      val allocator = EncoderAllocator.newArenaAllocator(16)
      val triples1 = (0 until 40).map(_ => allocator.newTriple())
      val quads1 = (0 until 34).map(_ => allocator.newQuad())

      // Make sure that the returned references are unique
      triples1.forall(t => triples1.count(t2 => t eq t2) == 1) should be(true)
      quads1.forall(q => quads1.count(q2 => q eq q2) == 1) should be(true)
    }

    "reuse messages when released" in {
      val allocator = EncoderAllocator.newArenaAllocator(16)
      val triples1 = (0 until 40).map(_ => allocator.newTriple())
      val quads1 = (0 until 40).map(_ => allocator.newQuad())

      allocator.releaseAll()

      val triples2 = (0 until 40).map(_ => allocator.newTriple())
      val quads2 = (0 until 40).map(_ => allocator.newQuad())

      for (i <- 0 until 16) {
        triples1(i) should be theSameInstanceAs (triples2(i))
        quads1(i) should be theSameInstanceAs (quads2(i))
      }

      // Messages outside the range of the arena should be allocated on the heap, so they
      // will not be the same instance.
      for (i <- 16 until 40) {
        triples1(i) should not be theSameInstanceAs (triples2(i))
        quads1(i) should not be theSameInstanceAs (quads2(i))
      }
    }

    "handle a large arena, limiting size to 2048" in {
      val allocator = EncoderAllocator.newArenaAllocator(2048)
      val triples1 = (0 until 3000).map(_ => allocator.newTriple())
      val quads1 = (0 until 3000).map(_ => allocator.newQuad())

      allocator.releaseAll()

      val triples2 = (0 until 3000).map(_ => allocator.newTriple())
      val quads2 = (0 until 3000).map(_ => allocator.newQuad())

      for (i <- 0 until 2048) {
        triples1(i) should be theSameInstanceAs (triples2(i))
        quads1(i) should be theSameInstanceAs (quads2(i))
      }

      // Messages outside the range of the arena should be allocated on the heap
      for (i <- 2048 until 3000) {
        triples1(i) should not be theSameInstanceAs (triples2(i))
        quads1(i) should not be theSameInstanceAs (quads2(i))
      }
    }
  }

