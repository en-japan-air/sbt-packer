addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0-M4")

{
  val pluginVersion = sys.props("plugin.version")
  if(pluginVersion == null)
    throw new RuntimeException("""|The system property 'plugin.version' is not defined.
                                  |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  addSbtPlugin("com.en-japan" % "sbt-packer" % pluginVersion)
}
