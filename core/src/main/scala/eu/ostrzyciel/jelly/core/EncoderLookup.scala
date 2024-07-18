package eu.ostrzyciel.jelly.core

import java.util

private[core] final case class EncoderValue(getId: Int, setId: Int, newEntry: Boolean)

private[core] final class EncoderLookup(maxEntries: Int)
  extends util.LinkedHashMap[String, EncoderValue](maxEntries + 2, 1, true):

  // 1-based index of the last assigned new value in the lookup table
  private var lastSetId: Int = 0
  private var lastAccessedKeyHash: Int = -1
  private var lastAccessedKey: String = ""
  private var lastAccessedValue: EncoderValue = null

  override def removeEldestEntry(eldest: util.Map.Entry[String, EncoderValue]): Boolean =
    size > maxEntries

  /**
   * Add a new entry to the cache
   * @param v value
   * @return (1-based id of the value, is a new entry)
   */
  def addEntry(v: String): EncoderValue =
    val vHash = v.hashCode
    if lastAccessedKeyHash == vHash && lastAccessedKey == v then
      // case 1: the value is the same as the last accessed one
      return lastAccessedValue

    lastAccessedKey = v
    lastAccessedKeyHash = vHash

    val value = this.get(v)
    if value != null then
      // case 2: the value is already in the map
      lastAccessedValue = value
      return value

    val s = this.size
    if s < maxEntries then
      // case 3: we still have free IDs, add it to the map
      lastSetId = s + 1
      lastAccessedValue = EncoderValue(lastSetId, lastSetId, false)
      this.put(v, lastAccessedValue)
      // the setId is always 0, because we haven't filled in the table yet
      return EncoderValue(lastSetId, 0, true)

    // case 4: no free IDs, reuse an old one
    lastAccessedValue = this.values.iterator.next
    this.put(v, lastAccessedValue)
    val getId = lastAccessedValue.getId
    val setId = if lastSetId + 1 == getId then 0 else getId
    lastSetId = getId
    EncoderValue(getId, setId, true)
