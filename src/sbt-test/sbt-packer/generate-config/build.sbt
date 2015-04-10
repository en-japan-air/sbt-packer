name := "app"

enablePlugins(JavaAppPackaging, JDebPackaging, PackerPlugin)

logBuffered := false

packerAmiRegions ++= Set("us-west-2", "ap-northeast-1")

packerAmiTags += ("Name" -> "AwesomeAmi")
packerAmiTags ++= Map("Memory" -> "800M", "Type" -> "Private")


TaskKey[Unit]("generate-valid-config") <<= (packerValidateConf, packerConfigFile, packerCommand, streams, packerAmiName, packerSourceAmi, packerInstanceType, packerRegion, packerAmiRegions, packerSshUsername, packerAmiTags) map { 
  (valid, conf, packerCmd, s, amiName, sourceAmi, instanceType, region, amiRegions, sshUsername, tags) =>
    assert(valid, "config doesn't generate a valid configuration")
    val config = IO.read(conf)
    assert(config.contains(amiName), "Config doesn't contain the right ami name" + config)
    assert(config.contains(sourceAmi), "Config doesn't contain the right source ami" + config)
    assert(config.contains(instanceType), "Config doesn't contain the right instance type" + config)
    assert(config.contains(region), "Config doesn't contain the right region" + config)
    assert(config.contains(sshUsername), s"Config doesn't contain the right username ($sshUsername)" + config)
    assert(tags forall { case (key,value) =>  config.contains(key) && config.contains(value) }, s"Config doesn't contain the right tags($tags)" + config)
    assert(amiRegions forall (config.split("\n").filter(_.contains("ami_regions"))(0) contains))
    s.log.info("Generated configuration validated")
}
