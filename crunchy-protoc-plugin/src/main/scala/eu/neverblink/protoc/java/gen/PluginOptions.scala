package eu.neverblink.protoc.java.gen

import com.google.protobuf.compiler.PluginProtos
import eu.neverblink.protoc.java.gen.PluginOptions.parseImplements

import java.lang.Boolean.*
import java.util
import java.util.regex.Pattern
import scala.jdk.CollectionConverters.*

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

object PluginOptions:
  private def parseIndentString(indent: String): String = indent match
      case "8" => "        "
      case "4" => "    "
      case "2" => "  "
      case "tab" => "\t"
      case _ => throw new Exception("Expected 2,4,8,tab. Found: " + indent)

  private def parseImplements(map: util.Map[String, String]): Map[String, Seq[String]] =
    map.asScala
      .filter((k, _) => k.startsWith("implements_"))
      .map((k, v) => (k.substring(11), v.split(";").toSeq))
      .toMap

class PluginOptions(request: PluginProtos.CodeGeneratorRequest):
  val map: util.Map[String, String] = ParserUtil.getGeneratorParameters(request)
  val indentString: String = PluginOptions.parseIndentString(map.getOrDefault("indent", "2"))
  val replacePackageFunction: String => String = parseReplacePackage(map.get("replace_package"))
  val generateDescriptors: Boolean = parseBoolean(map.getOrDefault("gen_descriptors", "true"))
  val implements: Map[String, Seq[String]] = parseImplements(map)
  val fastOneofMerge: Set[String] = map.getOrDefault("fast_oneof_merge", "").split(";").toSet
  val classBasedOneof: Set[String] = map.getOrDefault("class_based_oneof", "").split(";").toSet

  private def parseReplacePackage(replaceOption: String): String => String =
    // leave as is
    if (replaceOption == null) return str => str
    // parse "pattern=replacement"
    val parts = replaceOption.split("=")
    if (parts.length != 2) throw new Exception("'replace_package' expects 'pattern=replacement'. Found: '" + replaceOption + "'")
    // regex replace
    val pattern = Pattern.compile(parts(0))
    val replacement = parts(1)
    input => pattern.matcher(input).replaceAll(replacement)

