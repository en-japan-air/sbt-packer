package com.enjapan.sbt.packer

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.{ObjectMapper, PropertyNamingStrategy}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper


object Packer {

  val mapper = new ObjectMapper() with ScalaObjectMapper {
    registerModule(DefaultScalaModule)
    setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES)
    setSerializationInclusion(Include.NON_EMPTY)
  }

  case class PackerConfig(
    builders:Seq[Builder],
    provisioners:Seq[Provisioner],
    variables:Map[String,String] = Map())
  {
    def build = mapper.writeValueAsString(this)
  }

  protected trait TypedComponent {
    val `type`:String
  }

  trait Builder extends TypedComponent

  case class AmazonEbsBuilder(
    amiName:String,
    sourceAmi: String,
    instanceType: String,
    region: String,
    sshUsername: String,
    amiRegions: Set[String],
    tags: Map[String,String]
    ) extends Builder {
    val `type` = "amazon-ebs"
  }

  case class VirtualBoxBuilder(
    guestOsType: String,
    isoUrl: String,
    isoChecksum: String,
    isoChecksumType: String,
    sshUsername:String,
    sshPassword:String,
    sshWaitTimeout:String,
    shutdownCommand:String
    ) extends Builder {
    val `type` = "virtualbox-iso"
  }

  trait Provisioner extends TypedComponent

  case class FileProvisioner(source:String, destination:String) extends Provisioner {
    val `type` = "file"
  }

  case class ShellProvisioner(inline:Seq[String]) extends Provisioner {
    val `type` = "shell"
  }

  val installJava:ShellProvisioner = ShellProvisioner(Seq(
    "sudo add-apt-repository -y ppa:webupd8team/java",
    "sudo apt-get update",
    "sudo apt-get -y upgrade",
    "echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections",
    "sudo apt-get install -y oracle-java8-installer"
  ))
}