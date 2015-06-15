val Scalaversion = "2.11.6"
val Scalatraversion = "2.3.1"
val Jettyversion = "9.2.10.v20150310"

lazy val commonSettings = Seq(
  organization := "no.ndla",
  version := "0.1",
  scalaVersion := Scalaversion
)

lazy val image_api = (project in file(".")).
  settings(jetty(): _*).
  settings(commonSettings: _*).
  settings(
    name := "image-api",
    libraryDependencies ++= Seq(
      "org.scalatra" %% "scalatra" % Scalatraversion,
      "org.eclipse.jetty" % "jetty-webapp" % Jettyversion % "container;compile",
      "org.eclipse.jetty" % "jetty-plus" % Jettyversion % "container",
      "javax.servlet" % "javax.servlet-api" % "3.1.0" % "container;provided;test",
      "org.scalatra" %% "scalatra-json" % Scalatraversion,
      "org.json4s"   %% "json4s-jackson" % "3.2.11")
  ).enablePlugins(DockerPlugin)


assemblyJarName in assembly := "image-api.jar"
mainClass in assembly := Some("JettyLauncher")

// Make docker depend on the package task, which generates a jar file of the application code
docker <<= docker.dependsOn(sbt.Keys.`package`.in(Compile, packageBin))

// Define a Dockerfile
dockerfile in docker := {
  val jarFile = artifactPath.in(Compile, packageBin).value
  val classpath = (managedClasspath in Compile).value
  val mainclass = mainClass.in(Compile, packageBin).value.getOrElse(sys.error("Expected exactly one main class"))
  val jarTarget = s"/app/${jarFile.getName}"
  // Make a colon separated classpath with the JAR file
  val classpathString = classpath.files.map("/app/" + _.getName).mkString(":") + ":" + jarTarget
  new Dockerfile {
    // Base image
    from("java")
    // Add all files on the classpath
    add(classpath.files, "/app/")
    // Add the JAR file
    add(jarFile, jarTarget)
    // On launch run Java with the classpath and the main class
    entryPoint("java", "-cp", classpathString, mainclass)
  }
}

