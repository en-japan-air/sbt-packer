addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.6.4")

libraryDependencies <+= (sbtVersion) { sv =>
    "org.scala-sbt" % "scripted-plugin" % sv
}

resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
    url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
        Resolver.ivyStylePatterns)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.2")
