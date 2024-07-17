package eu.ostrzyciel.jelly.core

import java.util

private[core] final case class EncoderValue(getId: Int, setId: Int, newEntry: Boolean)

private[core] final class EncoderLookup(maxEntries: Int)
  extends util.LinkedHashMap[String, EncoderValue](maxEntries + 2, 1, true):

  // 1-based index of the last assigned new value in the lookup table
  private var lastSetId: Int = 0

  override def removeEldestEntry(eldest: util.Map.Entry[String, EncoderValue]): Boolean =
    size > maxEntries

  /**
   * Add a new entry to the cache
   * @param v value
   * @return (1-based id of the value, is a new entry)
   */
  def addEntry(v: String): EncoderValue =
    var setId: Int = 0
    var getId: Int = 0
    val value = this.computeIfAbsent(v, _ => {
      val s = this.size
      if s < maxEntries then
        // case 2a: there is still space in the map
        lastSetId = s + 1
        getId = lastSetId
        EncoderValue(lastSetId, lastSetId, false)
      else
        // case 2b: the map is full, we need to evict something
        val next = this.values.iterator.next
        getId = next.getId
        setId = if lastSetId + 1 == getId then 0 else getId
        lastSetId = getId
        next
    })
    // case 1: the value is already in the map
    if getId == 0 then value
    // case 2: we've added a new entry
    else EncoderValue(getId, setId, newEntry = true)
