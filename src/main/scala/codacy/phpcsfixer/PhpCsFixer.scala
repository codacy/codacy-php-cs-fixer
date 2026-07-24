package codacy.phpcsfixer

import java.io.File
import java.nio.file.{Path, Paths}

import com.codacy.plugins.api.{ErrorMessage, Options, Source}
import com.codacy.plugins.api.results.{Pattern, Result, Tool}
import com.codacy.tools.scala.seed.utils.{CommandRunner, FileHelper}
import com.codacy.tools.scala.seed.utils.ToolHelper._
import play.api.libs.json.{JsArray, JsBoolean, JsObject, JsValue, Json}

import scala.util.{Properties, Try}

object PhpCsFixer extends Tool {

  // php-cs-fixer's documented sentinel value for `--config` that tells it to ignore any
  // `.php-cs-fixer(.dist).php` present in the source directory (ConfigurationResolver::IGNORE_CONFIG_FILE).
  private[this] val ignoreConfigFile = "-"

  // Precedence matches php-cs-fixer's own: .php-cs-fixer.php (meant to be gitignored, for local
  // overrides) wins over .php-cs-fixer.dist.php when both exist.
  private[this] val configFilenames = Set(".php-cs-fixer.php", ".php-cs-fixer.dist.php")

  // Bitmask exit codes, see src/Console/Command/FixCommandExitStatusCalculator.php.
  // Only set when running `check` (i.e. dry-run):
  private[this] val hasInvalidFilesBit = 4 // some files have invalid PHP syntax
  private[this] val hasChangedFilesBit = 8 // some files need fixing - the "normal" case with results to parse
  // Anything else (1 = general/PHP-version error, 16 = app config error, 32 = fixer config error,
  // 64 = uncaught exception or lint errors remaining after fixing) means the run itself failed.
  private[this] val hardFailureBits = 1 | 16 | 32 | 64

  private[this] val invalidFileLineRegex = """^\s*\d+\)\s+(.+)$""".r

  // GitlabReporter (src/Console/Report/FixReport/GitlabReporter.php) always prefixes check_name with
  // "PHP-CS-Fixer.", but our patterns.json registers patternIds as the bare fixer name.
  private[this] val checkNamePrefix = "PHP-CS-Fixer."

  def apply(source: Source.Directory,
            configuration: Option[List[Pattern.Definition]],
            files: Option[Set[Source.File]],
            options: Map[Options.Key, Options.Value]
  )(implicit specification: Tool.Specification): Try[List[Result]] = {
    Try {
      val fullConfig = configuration.withDefaultParameters
      val filesToLint: List[String] = files.fold(List(source.toString)) { paths =>
        paths.map(_.toString).toList
      }

      // No Codacy pattern selection means "use the repository's own configuration" - explicitly look
      // for it (searching subdirectories too, matching the convention other Codacy tool wrappers use
      // via this same FileHelper) instead of leaning on php-cs-fixer's own auto-discovery, which only
      // ever looks in its current working directory.
      val configFile: Option[Path] =
        if (fullConfig.isEmpty) FileHelper.findConfigurationFile(Paths.get(source.path), configFilenames, maxDepth = 1) else None

      val command = getCommandFor(fullConfig, configFile, filesToLint)

      CommandRunner.exec(command, Option(new File(source.path))) match {
        case Right(resultFromTool) =>
          handleExitCode(resultFromTool.exitCode, resultFromTool.stdout, resultFromTool.stderr)
        case Left(failure) =>
          throw failure
      }
    }
  }

  private[this] def handleExitCode(exitCode: Int, stdout: List[String], stderr: List[String]): List[Result] = {
    if (exitCode == 0) {
      Nil
    } else if ((exitCode & hardFailureBits) != 0) {
      val msg =
        s"""
           |php-cs-fixer exited with code $exitCode
           |stdout: ${stdout.mkString(Properties.lineSeparator)}
           |stderr: ${stderr.mkString(Properties.lineSeparator)}
            """.stripMargin
      throw new Exception(msg)
    } else {
      val issues =
        if ((exitCode & hasChangedFilesBit) != 0) parseToolResult(stdout.mkString(Properties.lineSeparator)) else Nil
      val invalidFileErrors =
        if ((exitCode & hasInvalidFilesBit) != 0) parseInvalidFiles(stderr) else Nil

      issues ++ invalidFileErrors
    }
  }

  // Parses the `--format=gitlab` (CodeClimate) report. php-cs-fixer's own GitlabReporter already parses the
  // unified diff to compute a begin/end line range per applied fixer, so we get line-accurate issues for free.
  private[this] def parseToolResult(output: String): List[Result] = {
    Json.parse(output).as[JsArray].value.toList.map { item =>
      val filePath = (item \ "location" \ "path").as[String]
      val ruleId = (item \ "check_name").as[String].stripPrefix(checkNamePrefix)
      val message = (item \ "description").asOpt[String].getOrElse(ruleId)
      val line = (item \ "location" \ "lines" \ "begin").asOpt[Int].getOrElse(1)

      Result.Issue(Source.File(filePath), Result.Message(message), Pattern.Id(ruleId), Source.Line(line))
    }
  }

  // Files with invalid PHP syntax never make it into the gitlab report (php-cs-fixer only lists them in the
  // "linting before fixing" section of its human-readable error output), so we recover their paths from stderr.
  private[this] def parseInvalidFiles(stderr: List[String]): List[Result] = {
    stderr.collect { case invalidFileLineRegex(path) =>
      Result.FileError(Source.File(path.trim), Some(ErrorMessage("php-cs-fixer could not parse this file (invalid PHP syntax)")))
    }
  }

  private[this] def getCommandFor(configurationOpt: Option[List[Pattern.Definition]],
                                  configFile: Option[Path],
                                  filesToLint: List[String]
  ): List[String] = {
    val configFlags = configurationOpt match {
      case Some(patterns) =>
        // Codacy pattern selection always wins over any config file present in the repo.
        val rulesJson = JsObject(patterns.map(p => p.patternId.value -> ruleValue(p)))
        List(s"--config=$ignoreConfigFile", s"--rules=${Json.stringify(rulesJson)}")
      case None =>
        configFile match {
          case Some(path) => List(s"--config=$path")
          case None =>
            // No repo config file found either. We *must* still pass an explicit --config: if none is
            // given and more than one path ends up on the command line (Codacy commonly invokes us with
            // a chunk of individual file paths rather than the whole source directory, per splitChunkSize),
            // php-cs-fixer's own config resolution refuses to guess a shared directory and throws
            // "For multiple paths config parameter is required." `--config=-` sidesteps that resolution
            // entirely and falls through to php-cs-fixer's own built-in default ruleset, same as it would
            // for a single path.
            List(s"--config=$ignoreConfigFile")
        }
    }

    List("php-cs-fixer",
         "check",
         "-v",
         "--diff",
         "--format=gitlab",
         "--allow-risky=yes",
         "--using-cache=no",
         // Without a config file or --rules, php-cs-fixer >= 3.9x drops into an interactive setup wizard
         // and writes a `.php-cs-fixer.dist.php` into the analyzed directory. --no-interaction makes it
         // fall back to its built-in default ruleset instead, with no side effects on /src.
         "--no-interaction"
    ) ++ configFlags ++ filesToLint
  }

  private[this] def ruleValue(pattern: Pattern.Definition): JsValue = {
    if (pattern.parameters.isEmpty) {
      JsBoolean(true)
    } else {
      JsObject(pattern.parameters.toSeq.map(param => param.name.value -> (param.value: JsValue)))
    }
  }
}
