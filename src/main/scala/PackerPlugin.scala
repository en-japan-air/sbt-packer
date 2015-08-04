package com.enjapan.sbt.packer

import java.io.IOException
import java.net.URL

import scala.util.{Try,Success}
import scala.language.postfixOps

import sbt._
import sbt.Keys.{ packageBin, target, name, streams, version}

import com.typesafe.sbt.packager.archetypes.{JavaAppPackaging, TemplateWriter}
import com.typesafe.sbt.packager.debian.DebianPlugin.autoImport.Debian
import upickle._

object PackerPlugin extends AutoPlugin {

  override val requires = JavaAppPackaging

  import Packer._

  object autoImport {
    val packerVersion = settingKey[String]("Version of Packer to use")
    val packerAmiName = settingKey[String]("Name of the generated AMI")
    val packerSourceAmi = settingKey[String]("Id of the source AMI to generate from")
    val packerInstanceType = settingKey[String]("AWS Instance type")
    val packerRegion = settingKey[String]("AWS region")
    val packerAmiRegions = settingKey[Set[String]]("List of regions to copy the AMI to")
    val packerSshUsername = settingKey[String]("Username of the user to ssh with")
    val packerAmiTags = settingKey[Map[String,String]]("Tags to be associated with the AMI")

    val packerConfigFileOld = taskKey[File]("Generates a Packer configuration file")
    val packerBuild = taskKey[Unit]("Builds with Packer")

    val packerConfigTemplate = settingKey[java.net.URL]("Location of Packer configuration template")
    val packerConfigTemplateReplacements = settingKey[Seq[(String, String)]]("Replacements for Packer configuration template")

    val packerCommand = taskKey[String]("Use packer from the system or download it")

    val packerBuilders = taskKey[Seq[Builder]]("")
    val packerAmazonBuilder = taskKey[Builder]("")
    val packerProvisioners = taskKey[Seq[Provisioner]]("")
    val packerPackageInstallProvisioners = taskKey[Seq[Provisioner]]("")
    val packerConfig = taskKey[PackerConfig]("")
    val packerConfigFile = taskKey[File]("")
    val packerValidateConf = taskKey[Boolean]("")

    val packerUbuntuVersion = settingKey[String]("")
    val packerUbuntuHvm = settingKey[Boolean]("")
    val packerUbuntuEbs = settingKey[String]("")
    val packerUbuntuArch = settingKey[String]("")
  }

  import autoImport._

  override def projectSettings = Seq(
    packerVersion := "0.8.2",
    packerRegion := "us-east-1",
    packerAmiRegions := Set.empty,
    packerUbuntuVersion := "vivid",
    packerUbuntuHvm := true,
    packerUbuntuEbs := "ebs",
    packerUbuntuArch := "amd64",
    packerSourceAmi <<= (packerUbuntuVersion, packerUbuntuHvm, packerUbuntuEbs,packerUbuntuArch, packerRegion) { (ubuntuVersion,hvm,ebs,arch,region) =>
     UbuntuAMIFinder.find(version = ubuntuVersion, instanceType = ebs, hvm = hvm)(region)(arch)
    },
    packerInstanceType := "t2.micro",
    packerSshUsername := "ubuntu",
    packerAmiTags := Map.empty,
    packerAmiName <<= (name, version) { (n,v) =>  n + "-" + v + "-{{timestamp}}"},
    packerConfigFileOld := writePackerConfigOld(target.value, (packageBin in Debian).value, packerConfigTemplate.value, packerConfigTemplateReplacements.value),
    packerConfigTemplate := getPackerConfigTemplate,
    packerConfigTemplateReplacements <<= (packerAmiName, packerSourceAmi, packerInstanceType, packerRegion, packerAmiRegions, packerSshUsername) {
      (amiName, sourceAmi, instanceType, region, amiRegions, sshUsername) => 
        Seq(
          "ami_name" → amiName,
          "region" → region,
          "ami_regions" -> write(amiRegions.toSeq),
          "ssh_username" → sshUsername,
          "source_ami" → sourceAmi,
          "instance_type" → instanceType
        )
    },
    packerCommand <<= (packerVersion, target, streams) map { (v, t, s) => checkInstalledPacker(v,s.log).getOrElse(getPacker(v, t, s.log)) },
    packerBuild <<= (packerValidateConf, packerConfigFile, packerCommand) map { (valid, conf, cmd) => 
      if (!valid) throw new Exception(s"Packer configuration not valid (see ${conf.getAbsolutePath})")
      build(conf,cmd) 
    },
    packerAmazonBuilder <<= (packerAmiName, packerSourceAmi, packerInstanceType, packerRegion, packerAmiRegions, packerSshUsername, packerAmiTags) map {
          (amiName, sourceAmi, instanceType, region, amiRegions, sshUsername, tags) =>
            AmazonEbsBuilder(amiName, sourceAmi, instanceType, region, sshUsername, amiRegions, tags)
    },
    packerBuilders <<= packerAmazonBuilder map { b => Seq(b) },
    packerPackageInstallProvisioners <<=  (packageBin in Debian) map makeInstallProvisioners,
    packerProvisioners := (installJava +: packerPackageInstallProvisioners.value),
    packerConfig <<= (packerBuilders, packerProvisioners) map { (bs, ps) => PackerConfig(bs,ps) },
    packerConfigFile <<= (target, packerConfig) map { (t,p) => writePackerConfig(t,p) },
    packerValidateConf := {
      val conf = packerConfigFile.value
      val valid = validateConf(conf, packerCommand.value)
      if (!valid) throw new Exception(s"Packer configuration not valid (see ${conf.getAbsolutePath})")
      valid
    }
  )

  private[packer] def writePackerConfigOld(tmpDir: File, pkg:File, source: java.net.URL, replacements: Seq[(String, String)]): File = {
    val fileReplacements = Seq("source" → pkg.getAbsolutePath(),
        "destination" → ("/tmp/" + pkg.getName()))
    val fileContents = TemplateWriter.generateScript(source, fileReplacements ++ replacements)
    val nrFile = tmpDir / "tmp" / "packer.json"
    IO.write(nrFile, fileContents)
    nrFile
  }

  private[packer] def writePackerConfig(tmpDir: File, config:PackerConfig):File = {
    val nrFile = tmpDir / "packer" / "packer.json"
    IO.write(nrFile, config.build)
    nrFile
  }

  private[packer] def makeInstallProvisioners(pkg:File):Seq[Provisioner] = {
    val destination = "/tmp/" + pkg.getName()
    val copyFile = FileProvisioner(pkg.getAbsolutePath(), destination)
    val installPackage = ShellProvisioner(Seq(s"sudo dpkg -i ${destination}"))
    Seq(copyFile,installPackage)
  }

  private[packer] def validateConf(packerConf: File, packerCmd:String): Boolean = {
    Process(Seq(packerCmd, "validate", packerConf.getAbsolutePath())).! == 0
  }

  private[packer] def build(packerConf: File, packerCmd:String): Unit = {
    Process(Seq(packerCmd, "build", packerConf.getAbsolutePath())).!
  }

  private val packerBin = "packer"
  private[packer] def checkInstalledPacker(version:String, log:Logger):Option[String] = {
    Try{ Process(Seq(packerBin,"version")) !! } match {
      case Success(v) if v contains version => log.info("Found matching Packer in system, " + v); Some(packerBin)
      case Success(v) => log.info("Found Packer in the system not matching required version (expected:"+version+", found:"+v+")"); None
      case _ => None
    } 
  }

  private[packer] def getPacker(version:String, tmpDir:File, log:Logger):String = {
    val packerDir = (tmpDir / "packer" / (packerBin + "_" + version))
    val packerFile = (packerDir / packerBin)
    if ( !packerFile.exists ) {
      val os = sys.props.get("os.name").map(_.toLowerCase()).getOrElse(sys.error("Couldn't determine os")) match {
        case s if s.contains("linux") => "linux"
        case s if s.contains("mac") => "darwin"
        case s => sys.error("OS not supported: " + s)
      }
      val arch = sys.props.get("os.arch").map(_.toLowerCase()).getOrElse(sys.error("Couldn't determine arch")) match {
        case s if s.contains("64") => "amd64"
        case s if s.contains("86") => "386"
        case s => sys.error("Arch not supported: " + s)
      }
      val packerZip = "packer_" + version + "_" + os + "_" + arch + ".zip"
      val packerUrl = new URL("https://dl.bintray.com/mitchellh/packer/" + packerZip)
      log.info("Download Packer " + version + " at " + packerUrl)
      IO.unzipURL(packerUrl, packerDir) foreach { _.setExecutable(true) }
      log.info("Downloaded Packer")
    }
    else {
      log.info("Found previously downloaded Packer in " + packerDir.getAbsolutePath())
    }
    packerFile.getAbsolutePath()
  }

  protected def getPackerConfigTemplate: java.net.URL = getClass.getResource("packer.json.template")
}
