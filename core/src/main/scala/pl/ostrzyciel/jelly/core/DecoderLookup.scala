package pl.ostrzyciel.jelly.core

import pl.ostrzyciel.jelly.core.RDFProtobufDeserializationError

import scala.reflect.ClassTag

class DecoderLookup[T : ClassTag](maxEntries: Int):
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
