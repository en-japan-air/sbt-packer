package com.enjapan.sbt.packer

import upickle._
import scala.reflect.ClassTag


object Packer {
  case class PackerConfig( 
    builders:Seq[Builder], 
    provisioners:Seq[Provisioner], 
    variables:Map[String,String] = Map()) {

      object customConfig extends upickle.config.DefaultConfig {

        val myAnnotator: Annotator = new Annotator {
          val key = "type"

          private def camelToUnderscores(name: String) = "[A-Z]".r.replaceAllIn(name, {m =>
            "_" + m.group(0).toLowerCase()
          })

          override def annotate[V: ClassTag](w: Writer[V], name: String) = Writer[V] {
            case x: V => w.write(x) match {
              case o: Js.Obj => Js.Obj((key -> Js.Str(name)) +: (o.value map {case (k,v) => (camelToUnderscores(k),v)}) :_*)
              case o => Js.Arr(Js.Str(name), o)
            }
          }

          override def annotate[V](r: Reader[V], name: String) = Reader[V] {
            case o: Js.Obj if o.value.contains(key -> Js.Str(name)) => r.read(o)
            case Js.Arr(Js.Str(`name`), x) => r.read(x)
          }
        }

        override implicit val annotator = myAnnotator
      }
          
      import customConfig._

      implicit def BuilderW: Writer[Builder] = Writer[Builder] {
        case b:AmazonEbsBuilder => writeJs(b)
        case b:VirtualBoxBuilder => writeJs(b)
      }
      implicit def ProvisionerW: Writer[Provisioner] = Writer[Provisioner] {
        case p:FileProvisioner => writeJs(p)
        case p:ShellProvisioner => writeJs(p)
      }

      lazy val build = write(this)
  }

  sealed trait Builder

  @key("amazon-ebs") case class AmazonEbsBuilder(
    amiName:String,
    sourceAmi: String,
    instanceType: String,
    region: String,
    sshUsername: String,
    amiRegions: Set[String],
    tags: Map[String,String]
  ) extends Builder

  @key("virtualbox-iso") case class VirtualBoxBuilder(
    guestOsType: String,
    isoUrl: String,
    isoChecksum: String,
    isoChecksumType: String,
    sshUsername:String,
    sshPassword:String,
    sshWaitTimeout:String,
    shutdownCommand:String
  ) extends Builder

  sealed trait Provisioner 
  @key("file") case class FileProvisioner(source:String, destination:String) extends Provisioner
  @key("shell") case class ShellProvisioner(inline:Seq[String]) extends Provisioner 

  val installJava:ShellProvisioner = ShellProvisioner(Seq(
    "sudo add-apt-repository -y ppa:webupd8team/java",
    "sudo apt-get update",
    "echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections",
    "sudo apt-get install -y oracle-java8-installer"
  ))
}
