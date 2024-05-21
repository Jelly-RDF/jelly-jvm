package eu.ostrzyciel.jelly.core

import scala.reflect.ClassTag

/**
 * Simple, array-based lookup for the protobuf decoder.
 * @param maxEntries size of the pre-allocated lookup array
 * @tparam T type of the objects that will be stored in the lookup
 */
private[core] final class DecoderLookup[T : ClassTag](maxEntries: Int):
  // 0-based index of the last set value in the lookup
  private var lastSetId: Int = -1

  private val lookup = new Array[T](maxEntries)

  /**
   * @param id 1-based. 0 signifies an id that is larger by 1 than the last set id.
   * @param v value
   * @throws ArrayIndexOutOfBoundsException if id < 0 or id > maxEntries
   */
  def update(id: Int, v: T): Unit =
    if id == 0 then
      lastSetId += 1
      lookup(lastSetId) = v
    else
      lastSetId = id - 1
      lookup(lastSetId) = v

  /**
   * @param id 1-based
   * @return value
   * @throws ArrayIndexOutOfBoundsException if id < 1 or id > maxEntries
   */
  inline def get(id: Int): T = lookup(id - 1)
