package pl.ostrzyciel.jelly.core

import java.util

case class EncoderValue(id: Int, newEntry: Boolean)

class EncoderLookup(maxEntries: Int)
  extends util.LinkedHashMap[String, EncoderValue](maxEntries + 2, 1, true):

  override def removeEldestEntry(eldest: util.Map.Entry[String, EncoderValue]): Boolean =
    size > maxEntries

  /**
   * Add a new entry to the cache
   * @param v value
   * @return (1-based id of the value, is a new entry)
   */
  def addEntry(v: String): EncoderValue =
    val value = this.get(v)
    if value != null then
      // case 1: the value is already in the map
      return value

    val s = this.size
    if s < maxEntries then
      // case 2: we still have free IDs, add it to the map
      this.put(v, EncoderValue(s + 1, false))
      return EncoderValue(s + 1, true)

    // case 3: no free IDs, reuse an old one
    val next = this.values.iterator.next
    this.put(v, next)
    EncoderValue(next.id, true)
