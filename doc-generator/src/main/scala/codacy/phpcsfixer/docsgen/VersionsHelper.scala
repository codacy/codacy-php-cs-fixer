package codacy.phpcsfixer.docsgen

import better.files.File

object VersionsHelper {

  private[this] val properties = {
    val composerJsonString = File("composer.json").contentAsString
    val composerJson = ujson.read(composerJsonString)
    composerJson("require")
  }

  lazy val phpCsFixer: String = properties("friendsofphp/php-cs-fixer").str.replace("^", "")
}
