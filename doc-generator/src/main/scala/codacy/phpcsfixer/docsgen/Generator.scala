package codacy.phpcsfixer.docsgen

import better.files.File
import com.codacy.plugins.api.results.{Parameter, Pattern, Result, Tool}
import play.api.libs.json.{JsBoolean, JsNumber, JsString, JsValue, Json, Reads, Writes}
import com.codacy.plugins.api.languages.Languages

import scala.util.Properties

case class FixerOption(name: String, description: String, hasDefault: Boolean, default: Option[JsValue])

case class FixerDoc(name: String,
                    summary: String,
                    description: Option[String],
                    riskyDescription: Option[String],
                    category: String,
                    isRisky: Boolean,
                    isDeprecated: Boolean,
                    successors: List[String],
                    configuration: List[FixerOption],
                    codeSamples: List[CodeSample]
)

case class CodeSample(code: String, configuration: Option[JsValue])

case class FixersDump(fixers: List[FixerDoc], psr12Rules: List[String])

/** Generates docs/patterns.json and docs/description/ files from the JSON dump produced by
  * `dev-tools/dump-fixers.php`. Unlike codacy-codesniffer's doc-generator - which has to git-clone
  * and `phpdoc`-parse several external plugin repositories - php-cs-fixer exposes all fixer metadata
  * (summary, description, configuration options, risky/deprecated flags, code samples) through its own
  * PHP API in a single package, so one PHP script run is enough to source everything.
  */
class Generator {

  private[this] implicit val optionReads: Reads[FixerOption] = Json.reads[FixerOption]
  private[this] implicit val codeSampleReads: Reads[CodeSample] = Json.reads[CodeSample]
  private[this] implicit val fixerReads: Reads[FixerDoc] = Json.reads[FixerDoc]
  private[this] implicit val dumpReads: Reads[FixersDump] = Json.reads[FixersDump]

  private[this] val toolName = Tool.Name("php-cs-fixer")
  private[this] val toolVersion = Option(Tool.Version(VersionsHelper.phpCsFixer))

  val docsDir: File = File("./docs")
  val patternsFile: File = docsDir / "patterns.json"
  val descriptionsDir: File = docsDir / "description"
  val descriptionFile: File = descriptionsDir / "description.json"

  def run(): Unit = {
    docsDir.createDirectories()
    descriptionsDir.createDirectories()

    val dump = loadDump()
    val psr12Rules = dump.psr12Rules.toSet

    val specifications = dump.fixers.map(toSpecification(_, psr12Rules)).toSet
    val descriptions = dump.fixers.map(toDescription).sortBy(_.patternId.value)

    val toolSpecification = Tool.Specification(toolName, toolVersion, specifications)

    writeAsJsonToFile(toolSpecification, patternsFile)
    writeAsJsonToFile(descriptions, descriptionFile)

    dump.fixers.foreach { fixer =>
      (descriptionsDir / s"${fixer.name}.md").overwrite(fullDescription(fixer))
    }
  }

  // Produced by running `php dev-tools/dump-fixers.php > fixers-dump.json` in a plain PHP build stage
  // (see Dockerfile) and copied alongside this sbt project, so this JVM-only subproject never needs a
  // PHP runtime of its own.
  private[this] def loadDump(): FixersDump = {
    Json.parse(File("fixers-dump.json").contentAsString).as[FixersDump]
  }

  private[this] def toSpecification(fixer: FixerDoc, psr12Rules: Set[String]): Pattern.Specification = {
    val level = if (fixer.isRisky) Result.Level.Warn else Result.Level.Info
    val parameters = configurableOptions(fixer).view.map { case (option, default) =>
      Parameter.Specification(Parameter.Name(option.name), Parameter.Value(default))
    }.toSet

    Pattern.Specification(patternId = Pattern.Id(fixer.name),
                          level = level,
                          category = RuleCategories.byPatternId.getOrElse(fixer.name, Pattern.Category.CodeStyle),
                          subcategory = None,
                          languages = Set(Languages.PHP),
                          parameters = parameters,
                          enabled = psr12Rules.contains(fixer.name) && !fixer.isDeprecated
    )
  }

  private[this] def toDescription(fixer: FixerDoc): Pattern.Description = {
    val parameters = configurableOptions(fixer).view.map { case (option, _) =>
      Parameter.Description(Parameter.Name(option.name), Parameter.DescriptionText(option.description))
    }.toSet

    Pattern.Description(patternId = Pattern.Id(fixer.name),
                        title = Pattern.Title(truncateTitle(fixer.summary)),
                        description = Some(Pattern.DescriptionText(fixer.summary)),
                        timeToFix = None,
                        parameters = parameters
    )
  }

  // Most fixers' getSummary() is a short one-liner, but a few (e.g. modifier_keywords,
  // visibility_required) return a full paragraph - Codacy caps pattern titles at 255 characters.
  private[this] val maxTitleLength = 255

  private[this] def truncateTitle(summary: String): String = {
    if (summary.length <= maxTitleLength) summary
    else summary.take(maxTitleLength - 1).trim + "…"
  }

  // Codacy's Parameter.Value model only round-trips simple scalars: withDefaultParameters plucks a
  // parameter's stored default straight out of patterns.json and hands it to us unchanged as-is (no
  // re-parsing), so whatever we store here is exactly what gets sent back to php-cs-fixer's --rules for
  // any parameter Codacy's config doesn't explicitly set. Options whose real PHP default is an array or
  // null (e.g. ordered_imports' `imports_order`) can't be represented as a Parameter.Value string without
  // producing an invalid value (php-cs-fixer expects `string[]|null`, not the literal string "" or "null"),
  // so those are left out of `parameters` entirely - the rule stays fully usable, just not independently
  // configurable for that one option, and php-cs-fixer's own built-in default silently applies instead.
  private[this] def configurableOptions(fixer: FixerDoc): List[(FixerOption, String)] = {
    fixer.configuration.flatMap(option => scalarDefaultAsString(option).map(option -> _))
  }

  // Parameter.Value's string constructor sniffs its input as JSON before falling back to a literal
  // string, so an unquoted string default that happens to look like a number/bool/null (e.g. the "10.0"
  // PHPUnit target-version default) gets silently reinterpreted as that other JSON type - which then
  // fails php-cs-fixer's own type validation once sent back through --rules. Re-quoting string defaults
  // here forces them to keep parsing back as JsString. Boolean/number defaults are left unquoted so they
  // *do* round-trip as their real type, which options with a genuine bool/int default expect.
  private[this] def scalarDefaultAsString(option: FixerOption): Option[String] = {
    option.default match {
      case Some(value @ JsString(_)) => Some(Json.stringify(value))
      case Some(value @ (_: JsBoolean | _: JsNumber)) => Some(Json.stringify(value))
      case _ => None
    }
  }

  private[this] def fullDescription(fixer: FixerDoc): String = {
    val builder = new StringBuilder(fixer.summary)
    builder.append(Properties.lineSeparator)

    fixer.description.foreach { description =>
      builder.append(Properties.lineSeparator).append(description).append(Properties.lineSeparator)
    }

    fixer.riskyDescription.foreach { riskyDescription =>
      builder.append(Properties.lineSeparator).append("**Risky:** ").append(riskyDescription).append(Properties.lineSeparator)
    }

    if (fixer.isDeprecated && fixer.successors.nonEmpty) {
      builder
        .append(Properties.lineSeparator)
        .append("**Deprecated**, use instead: ")
        .append(fixer.successors.mkString(", "))
        .append(Properties.lineSeparator)
    }

    fixer.codeSamples.headOption.foreach { sample =>
      builder
        .append(Properties.lineSeparator)
        .append("```php")
        .append(Properties.lineSeparator)
        .append(sample.code)
        .append(Properties.lineSeparator)
        .append("```")
        .append(Properties.lineSeparator)
    }

    builder.toString()
  }

  private[this] def writeAsJsonToFile[A: Writes](a: A, file: File): File = {
    file.overwrite(Json.prettyPrint(Json.toJson(a)))
  }
}
