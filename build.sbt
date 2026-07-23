ThisBuild / scalaVersion := "2.13.14"

lazy val `doc-generator` = project
  .settings(
    libraryDependencies ++= Seq("com.codacy" %% "codacy-engine-scala-seed" % "6.1.4",
                                "com.lihaoyi" %% "ujson" % "4.4.3",
                                "com.github.pathikrit" %% "better-files" % "3.9.2"
    )
  )

lazy val root = project
  .in(file("."))
  .settings(name := "codacy-php-cs-fixer",
            libraryDependencies ++= Seq("com.codacy" %% "codacy-engine-scala-seed" % "6.1.4"),
            mainClass in Compile := Some("codacy.Engine"),
            nativeImageOptions ++= List("-O1",
                                        "-H:+ReportExceptionStackTraces",
                                        "--no-fallback",
                                        "--no-server",
                                        "-J-Xmx8G",
                                        "-J-XX:ActiveProcessorCount=8"
            )
  )
  .enablePlugins(NativeImagePlugin)
  .enablePlugins(JavaAppPackaging)
