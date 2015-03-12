import com.enjapan.sbt.packer.Packer._
import com.enjapan.sbt.packer.UbuntuAMIFinder

name := "app"

enablePlugins(JavaAppPackaging, JDebPackaging, PackerPlugin)

logBuffered := false

val testShellLine = "Amazing Line"

packerProvisioners += ShellProvisioner(Seq(testShellLine))

val testBuilder = VirtualBoxBuilder(
  guestOsType = "Ubuntu_64",
    isoUrl = "http://releases.ubuntu.com/12.04/ubuntu-12.04.5-server-amd64.iso",
    isoChecksum = "769474248a3897f4865817446f9a4a53",
    isoChecksumType = "md5",
    sshUsername = "packer",
    sshPassword = "packer",
    sshWaitTimeout = "30s",
    shutdownCommand = "echo 'packer' | sudo -S shutdown -P now"
)

packerBuilders += testBuilder

TaskKey[Unit]("generate-valid-config") <<= (packerValidateConf, packerConfigFile, streams) map { 
  (valid, conf, s) =>
    assert(valid, "config doesn't generate a valid configuration")
    val config = IO.read(conf)
    assert(config.contains(testBuilder.isoUrl), "Config doesn't contain the right ami name" + config)
    assert(config.contains(testBuilder.isoChecksum), "Config doesn't contain the right source ami" + config)
    assert(config.contains(testBuilder.isoChecksumType), "Config doesn't contain the right instance type" + config)
    assert(config.contains(testBuilder.sshPassword), "Config doesn't contain the right region" + config)
    assert(config.contains(testBuilder.sshUsername), "Config doesn't contain the right username" + config)
    assert(config.contains(testBuilder.sshWaitTimeout), "Config doesn't contain the right username" + config)
    assert(config.contains(testBuilder.shutdownCommand), "Config doesn't contain the right username" + config)
    s.log.info("Generated configuration validated")
}
