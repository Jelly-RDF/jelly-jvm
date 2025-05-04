package eu.neverblink.protoc.java.gen

import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}

import java.io.{ByteArrayOutputStream, PrintStream, UnsupportedEncodingException}
import java.nio.charset.StandardCharsets
import java.util
import java.util.Collections

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

/**
 * @author Florian Enner
 * @author Piotr Sowiński
 */
object ParserUtil {
  def getGeneratorParameters(request: CodeGeneratorRequest): util.Map[String, String] =
    if (!request.hasParameter) return Collections.emptyMap
    parseGeneratorParameters(request.getParameter)

  /**
   * Returns a map of input arguments added before the proto path, e.g.,
   * <p>
   * PROTOC INPUT: "--GEN_out=option1=value1,option2=value2,optionFlag3:./my-output-directory"
   * PARAMETER STRING: "option1=value1,option2=value2,optionFlag3"
   *
   * @param parameter parameter string input into protoc
   * @return map
   */
  private def parseGeneratorParameters(parameter: String): util.Map[String, String] =
    if (parameter == null || parameter.isEmpty) return Collections.emptyMap
    val map = new util.HashMap[String, String]
    val parts = parameter.split(",")
    for (part <- parts) {
      val equalsIndex = part.indexOf("=")
      if (equalsIndex == -1) map.put(part, "")
      else {
        val key = part.substring(0, equalsIndex)
        val value = part.substring(equalsIndex + 1)
        map.put(key, value)
      }
    }
    map

  def asErrorWithStackTrace(e: Exception): CodeGeneratorResponse =
    // Print error with StackTrace
    val baos = new ByteArrayOutputStream
    val ps = new PrintStream(baos, true, "UTF-8")
    try e.printStackTrace(ps)
    catch {
      case ex: UnsupportedEncodingException =>
        throw new AssertionError("UTF-8 encoding not supported")
    } finally if (ps != null) ps.close()
    val errorWithStackTrace = new String(baos.toByteArray, StandardCharsets.UTF_8)
    CodeGeneratorResponse.newBuilder.setError(errorWithStackTrace).build

  def asError(errorMessage: String): CodeGeneratorResponse = 
    CodeGeneratorResponse.newBuilder.setError(errorMessage).build
}
