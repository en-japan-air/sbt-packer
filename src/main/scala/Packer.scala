package com.enjapan.sbt.packer

import org.json4s._
import org.json4s.native.JsonMethods._

object Packer {

  private def camelToUnderscores(name: String) = "[A-Z]".r.replaceAllIn(name, {m =>
    "_" + m.group(0).toLowerCase()
  })

  val typedComponentSerializer = FieldSerializer[TypedComponent](
  { case (key,v) => Some(camelToUnderscores(key),v) }
  )

  case class PackerConfig( 
    builders:Seq[Builder], 
    provisioners:Seq[Provisioner], 
    variables:Map[String,String] =
      Map("aws_access_key" -> "{{env `AWS_ACCESS_KEY_ID`}}", "aws_secret_key" -> "{{env `AWS_SECRET_ACCESS_KEY`}}" )) 
  {
    implicit val formats = DefaultFormats + typedComponentSerializer
    lazy val toJson = Extraction.decompose(this) 
    lazy val build = pretty(render(toJson))
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
    tags: Map[String,String],
    accessKey:String = "{{user `aws_access_key`}}",
    secretKey: String = "{{user `aws_secret_key`}}"
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
    "echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections",
    "sudo apt-get install -y oracle-java8-installer"
    ))
}
