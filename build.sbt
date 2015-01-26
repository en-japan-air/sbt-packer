sbtPlugin := true

scalaVersion in Global := "2.10.2"

organization := "com.en-japan"

name := "sbt-packer"

scalacOptions in Compile ++= Seq("-deprecation", "-target:jvm-1.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0-M4" % "provided")

// Scripted
ScriptedPlugin.scriptedSettings

scriptedLaunchOpts <+= version apply { v => "-Dplugin.version="+v }

// Publish settings
publishMavenStyle := false

bintraySettings

bintrayPublishSettings

bintray.Keys.bintrayOrganization in bintray.Keys.bintray := Some("en-japan")

bintray.Keys.repository in bintray.Keys.bintray := Some("generic")

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("sbt", "packer", "sbt-native-packager")

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))