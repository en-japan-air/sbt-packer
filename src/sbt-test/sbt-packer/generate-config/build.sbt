name := "app"

enablePlugins(JavaAppPackaging, JDebPackaging, PackerPlugin)

logBuffered := false

packerAmiRegions ++= Set("us-west-2", "ap-northeast-1")

TaskKey[Unit]("generate-valid-config") <<= (packerConfig, packerCommand, streams, packerAmiName, packerSourceAmi, packerInstanceType, packerRegion, packerAmiRegions, packerSshUsername) map { 
  (conf, packerCmd, s, amiName, sourceAmi, instanceType, region, amiRegions, sshUsername) =>
    val validateCode = Process(Seq(packerCmd, "validate", conf.getAbsolutePath())).!(s.log)
    assert(validateCode == 0, "config doesn't generate a valid configuration")
    val config = IO.read(conf)
    assert(config.contains(amiName), "Config doesn't contain the right ami name" + config)
    assert(config.contains(sourceAmi), "Config doesn't contain the right source ami" + config)
    assert(config.contains(instanceType), "Config doesn't contain the right instance type" + config)
    assert(config.contains(region), "Config doesn't contain the right region" + config)
    assert(config.contains(sshUsername), "Config doesn't contain the right username" + config)
    assert(amiRegions forall (config.split("\n").filter(_.contains("ami_regions"))(0) contains))
    s.log.info("Generated configuration validated")
}
