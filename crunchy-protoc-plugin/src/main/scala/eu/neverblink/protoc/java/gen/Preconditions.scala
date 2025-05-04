package eu.neverblink.protoc.java.gen

/*-
 * #%L
 * quickbuf-generator / CrunchyProtocPlugin
 * %%
 * Copyright (C) 2019 HEBI Robotics
 * %%
 * Copyright (C) 2025 NeverBlink
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

object Preconditions:
  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws IllegalArgumentException if  <pre>expression</pre>  is false
   */
  def checkArgument(expression: Boolean): Unit =
    if (!expression) throw new IllegalArgumentException

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression   a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   *                     string using [[String# valueOf ( Object )]]
   * @throws IllegalArgumentException if  <pre>expression</pre>  is false
   */
  def checkArgument(expression: Boolean, errorMessage: AnyRef): Unit =
    if (!expression) throw new IllegalArgumentException(String.valueOf(errorMessage))

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression           a boolean expression
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *                             message is formed by replacing each  <pre>%s</pre>  placeholder in the template with an
   *                             argument. These are matched by position - the first  <pre>%s</pre>  gets
   *                             <pre>errorMessageArgs[0]</pre>, etc.  Unmatched arguments will be appended to the formatted message
   *                             in square braces. Unmatched placeholders will be left as-is.
   * @throws IllegalArgumentException if  <pre>expression</pre>  is false
   * @throws NullPointerException     if the check fails and either  <pre>errorMessageTemplate</pre>  or
   *                                  <pre>errorMessageArgs</pre>  is null (don't let this happen)
   */
  def checkArgument(expression: Boolean, errorMessageTemplate: String, errorMessageArgs: AnyRef*): Unit =
    if (!expression) throw new IllegalArgumentException(format(errorMessageTemplate, errorMessageArgs))

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws IllegalStateException if  <pre>expression</pre>  is false
   */
  def checkState(expression: Boolean): Unit =
    if (!expression) throw new IllegalStateException

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression           a boolean expression
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *                             message is formed by replacing each  <pre>%s</pre>  placeholder in the template with an
   *                             argument. These are matched by position - the first  <pre>%s</pre>
   *                             gets <pre>errorMessageArgs[0]</pre>, etc.
   *                             Unmatched arguments will be appended to the formatted message
   *                             in square braces. Unmatched placeholders will be left as-is.
   * @throws IllegalStateException if  <pre>expression</pre>  is false
   * @throws NullPointerException if the check fails and either  <pre>errorMessageTemplate</pre>  or
   *                              <pre>errorMessageArgs</pre>  is null (don't let this happen)
   */
  def checkState(expression: Boolean, errorMessageTemplate: String, errorMessageArgs: AnyRef*): Unit =
    if (!expression) throw new IllegalStateException(format(errorMessageTemplate, errorMessageArgs))

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @return the non-null reference that was validated
   * @throws NullPointerException if  <pre>reference</pre>  is null
   */
  def checkNotNull[T](reference: T): T =
    if (reference == null) throw new NullPointerException
    reference

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference            an object reference
   * @param errorMessageTemplate a template for the exception message should the check fail. The
   *                             message is formed by replacing each  <pre>%s</pre>  placeholder in the template with an
   *                             argument. These are matched by position - the first  <pre>%s</pre>
   *                             gets <pre>errorMessageArgs[0]</pre>, etc.
   *                             Unmatched arguments will be appended to the formatted message
   *                             in square braces. Unmatched placeholders will be left as-is.
   * @return the non-null reference that was validated
   * @throws NullPointerException if  <pre>reference</pre>  is null
   */
  def checkNotNull[T](reference: T, errorMessageTemplate: String, errorMessageArgs: AnyRef*): T =
    if (reference == null) {
      // If either of these parameters is null, the right thing happens anyway
      throw new NullPointerException(format(errorMessageTemplate, errorMessageArgs))
    }
    reference

  /**
   * Ensures that <pre>index</pre> specifies a valid <i>element</i> in an array, list or string of size
   * <pre>size</pre>. An element index may range from zero, inclusive, to <pre>size</pre>, exclusive.
   *
   * @param index a user-supplied index identifying an element of an array, list or string
   * @param size  the size of that array, list or string
   * @return the value of  <pre>index</pre>
   * @throws IndexOutOfBoundsException if  <pre>index</pre>  is negative or is not less than  <pre>size</pre>
   * @throws IllegalArgumentException  if  <pre>size</pre>  is negative
   */
  /*
       * All recent hotspots (as of 2009) *really* like to have the natural code
       *
       * if (guardExpression) {
       *    throw new BadException(messageExpression);
       * }
       *
       * refactored so that messageExpression is moved to a separate String-returning method.
       *
       * if (guardExpression) {
       *    throw new BadException(badMsg(...));
       * }
       *
       * The alternative natural refactorings into void or Exception-returning methods are much slower.
       * This is a big deal - we're talking factors of 2-8 in microbenchmarks, not just 10-20%.  (This
       * is a hotspot optimizer bug, which should be fixed, but that's a separate, big project).
       *
       * The coding pattern above is heavily used in java.util, e.g. in ArrayList.  There is a
       * RangeCheckMicroBenchmark in the JDK that was used to test this.
       *
       * But the methods in this class want to throw different exceptions, depending on the args, so it
       * appears that this pattern is not directly applicable.  But we can use the ridiculous, devious
       * trick of throwing an exception in the middle of the construction of another exception.  Hotspot
       * is fine with that.
       */
  def checkElementIndex(index: Int, size: Int): Int = checkElementIndex(index, size, "index")

  /**
   * Ensures that <pre>index</pre> specifies a valid <i>element</i> in an array, list or string of size
   * <pre>size</pre>. An element index may range from zero, inclusive, to <pre>size</pre>, exclusive.
   *
   * @param index a user-supplied index identifying an element of an array, list or string
   * @param size  the size of that array, list or string
   * @param desc  the text to use to describe this index in an error message
   * @return the value of  <pre>index</pre>
   * @throws IndexOutOfBoundsException if  <pre>index</pre>  is negative or is not less than  <pre>size</pre>
   * @throws IllegalArgumentException  if  <pre>size</pre>  is negative
   */
  private def checkElementIndex(index: Int, size: Int, desc: String): Int =
    // Carefully optimized for execution by hotspot (explanatory comment above)
    if (index < 0 || index >= size) throw new IndexOutOfBoundsException(badElementIndex(index, size, desc))
    index

  private def badElementIndex(index: Int, size: Int, desc: String) =
    if (index < 0) format("%s (%s) must not be negative", desc, index)
    else if (size < 0) throw new IllegalArgumentException("negative size: " + size)
    else { // index >= size
      format("%s (%s) must be less than size (%s)", desc, index, size)
    }

  /**
   * Ensures that <pre>index</pre> specifies a valid <i>position</i> in an array, list or string of
   * size <pre>size</pre>. A position index may range from zero to <pre>size</pre>, inclusive.
   *
   * @param index a user-supplied index identifying a position in an array, list or string
   * @param size  the size of that array, list or string
   * @return the value of  <pre>index</pre>
   * @throws IndexOutOfBoundsException if  <pre>index</pre>  is negative or is greater than  <pre>size</pre>
   * @throws IllegalArgumentException  if  <pre>size</pre>  is negative
   */
  def checkPositionIndex(index: Int, size: Int): Int = checkPositionIndex(index, size, "index")

  /**
   * Ensures that <pre>index</pre> specifies a valid <i>position</i> in an array, list or string of
   * size <pre>size</pre>. A position index may range from zero to <pre>size</pre>, inclusive.
   *
   * @param index a user-supplied index identifying a position in an array, list or string
   * @param size  the size of that array, list or string
   * @param desc  the text to use to describe this index in an error message
   * @return the value of  <pre>index</pre>
   * @throws IndexOutOfBoundsException if  <pre>index</pre>  is negative or is greater than  <pre>size</pre>
   * @throws IllegalArgumentException  if  <pre>size</pre>  is negative
   */
  private def checkPositionIndex(index: Int, size: Int, desc: String): Int = {
    // Carefully optimized for execution by hotspot (explanatory comment above)
    if (index < 0 || index > size) throw new IndexOutOfBoundsException(badPositionIndex(index, size, desc))
    index
  }

  private def badPositionIndex(index: Int, size: Int, desc: String) = if (index < 0) format("%s (%s) must not be negative", desc, index)
  else if (size < 0) throw new IllegalArgumentException("negative size: " + size)
  else { // index > size
    format("%s (%s) must not be greater than size (%s)", desc, index, size)
  }

  /**
   * Ensures that <pre>start</pre> and <pre>end</pre> specify a valid <i>positions</i> in an array, list
   * or string of size <pre>size</pre>, and are in order. A position index may range from zero to
   * <pre>size</pre>, inclusive.
   *
   * @param start a user-supplied index identifying a starting position in an array, list or string
   * @param end   a user-supplied index identifying a ending position in an array, list or string
   * @param size  the size of that array, list or string
   * @throws IndexOutOfBoundsException if either index is negative or is greater than  <pre>size</pre> ,
   *                                   or if  <pre>end</pre>  is less than  <pre>start</pre>
   * @throws IllegalArgumentException  if  <pre>size</pre>  is negative
   */
  def checkPositionIndexes(start: Int, end: Int, size: Int): Unit = {
    // Carefully optimized for execution by hotspot (explanatory comment above)
    if (start < 0 || end < start || end > size) throw new IndexOutOfBoundsException(badPositionIndexes(start, end, size))
  }

  private def badPositionIndexes(start: Int, end: Int, size: Int): String = {
    if (start < 0 || start > size) return badPositionIndex(start, size, "start index")
    if (end < 0 || end > size) return badPositionIndex(end, size, "end index")
    // end < start
    format("end index (%s) must not be less than start index (%s)", end, start)
  }

  /**
   * Substitutes each <pre>%s</pre> in <pre>template</pre> with an argument. These are matched by
   * position: the first <pre>%s</pre> gets <pre>args[0]</pre>, etc.  If there are more arguments than
   * placeholders, the unmatched arguments will be appended to the end of the formatted message in
   * square braces.
   *
   * @param template a non-null string containing 0 or more  <pre>%s</pre>  placeholders.
   * @param args the arguments to be substituted into the message template. Arguments are converted
   *             to strings using [[String# valueOf ( Object )]]. Arguments can be null.
   */
  // Note that this is somewhat-improperly used from Verify.java as well.
  def format(template: String, args: Any*): String =
    val template2 = String.valueOf(template) // null -> "null"

    // start substituting the arguments into the '%s' placeholders
    val builder = new StringBuilder(template2.length + 16 * args.length)
    var templateStart = 0
    var i = 0
    var continue = true
    while (i < args.length && continue) {
      val placeholderStart = template2.indexOf("%s", templateStart)
      if placeholderStart == -1 then continue = false
      else
        builder.append(template2, templateStart, placeholderStart)
        builder.append(args({
          i += 1; i - 1
        }))
        templateStart = placeholderStart + 2
    }
    builder.append(template2.substring(templateStart))
    // if we run out of placeholders, append the extra args in square braces
    if (i < args.length) {
      builder.append(" [")
      builder.append(args({
        i += 1; i - 1
      }))
      while (i < args.length) {
        builder.append(", ")
        builder.append(args({
          i += 1; i - 1
        }))
      }
      builder.append(']')
    }
    builder.toString
