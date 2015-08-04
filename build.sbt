sbtPlugin := true

scalaVersion in Global := "2.10.4"

organization := "com.en-japan"

name := "sbt-packer"

scalacOptions in Compile ++= Seq("-feature", "-deprecation", "-target:jvm-1.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.1" % "provided")

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.6.0-1")

// Scripted
ScriptedPlugin.scriptedSettings

scriptedLaunchOpts <+= version apply { v => "-Dplugin.version="+v }

// Publish settings
publishMavenStyle := false

bintrayOrganization := Some("en-japan")

bintrayRepository := "sbt"

bintrayPackageLabels := Seq("sbt", "packer", "sbt-native-packager")

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
