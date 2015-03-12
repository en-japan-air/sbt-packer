package com.enjapan.sbt.packer

import org.json4s._
import org.json4s.JsonDSL._

object Packer {

  def camelToUnderscores(name: String) = "[A-Z]".r.replaceAllIn(name, {m =>
    "_" + m.group(0).toLowerCase()
  })

  implicit val formats = DefaultFormats + FieldSerializer[TypedComponent](
  { case (key,v) => Some(camelToUnderscores(key),v) }
  )

  case class PackerBuild( 
    builders:Seq[Builder], 
    provisioners:Seq[Provisioner], 
    variables:Map[String,String] =
      Map("aws_access_key" -> "{{env `AWS_ACCESS_KEY_ID`}}", "aws_secret_key" -> "{{env `AWS_SECRET_ACCESS_KEY`}}" )) 
  {
    def toJson = Extraction.decompose(this) 
  }

  protected trait TypedComponent {
    val `type`:String
  }

  trait Builder extends TypedComponent

  case class AmazonEbsBuilder(
    sourceAmi: String,
    instanceType: String,
    sshUsername: String,
    region: String,
    accessKey:String = "{{user `aws_access_key`}}",
    secretKey: String = "{{user `aws_secret_key`}}",
    amiRegions: Seq[String] = Seq.empty
  ) extends Builder {
    val `type` = "amazon-ebs"
  }

  trait Provisioner extends TypedComponent

  case class FileProvisioner(source:String, destination:String) extends Provisioner {
    val `type` = "file"
  }

  case class ShellProvisioner(lines:Seq[String]) extends Provisioner {
    val `type` = "shell"
  }


  val installJava = ShellProvisioner(Seq(
    "sudo add-apt-repository -y ppa:webupd8team/java",
    "sudo apt-get update",
    "echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections",
    "sudo apt-get install -y oracle-java8-installer"
    ))
}
