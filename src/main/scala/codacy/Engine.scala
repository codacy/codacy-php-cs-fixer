package codacy

import codacy.phpcsfixer.PhpCsFixer
import com.codacy.tools.scala.seed.DockerEngine

object Engine extends DockerEngine(PhpCsFixer)()
