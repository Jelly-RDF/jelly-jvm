package pl.ostrzyciel.jelly.core

import pl.ostrzyciel.jelly.core.RDFProtobufDeserializationError

import scala.reflect.ClassTag

/**
 * Simple, array-based lookup for the protobuf decoder.
 * @param maxEntries size of the pre-allocated lookup array
 * @tparam T type of the objects that will be stored in the lookup
 */
private[core] final class DecoderLookup[T : ClassTag](maxEntries: Int):
  private val lookup = new Array[T](maxEntries)

  /**
   * @param id 1-based
   * @param v value
   */
  inline def update(id: Int, v: T): Unit = lookup(id - 1) = v

  /**
   * @param id 1-based
   * @return value
   */
  inline def get(id: Int): T = lookup(id - 1)
