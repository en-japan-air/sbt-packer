package com.enjapan.sbt.packer

import scala.util.{Try,Success}

import sbt._
import sbt.Keys.{ packageBin, target, name, streams, version}

import com.typesafe.sbt.packager.archetypes.{JavaAppPackaging, TemplateWriter}
import com.typesafe.sbt.packager.debian.DebianPlugin.autoImport.Debian
import scala.language.postfixOps


object PackerPlugin extends AutoPlugin {

  override val requires = JavaAppPackaging

  object autoImport {
    val packerVersion = settingKey[String]("Version of Packer to use")
    val packerAmiName = settingKey[String]("Name of the generated AMI")
    val packerSourceAmi = settingKey[String]("Id of the source AMI to generate from")
    val packerInstanceType = settingKey[String]("AWS Instance type")
    val packerRegion = settingKey[String]("AWS region")
    val packerSshUsername = settingKey[String]("Username of the user to ssh with")

    val packerConfig = taskKey[File]("Generates a Packer configuration file")
    val packerBuildAmi = taskKey[Unit]("Builds a new amazon AMI")

    val packerConfigTemplate = settingKey[java.net.URL]("Location of Packer configuration template")
    val packerConfigTemplateReplacements = settingKey[Seq[(String, String)]]("Replacements for Packer configuration template")

    val packerCommand = taskKey[String]("Use packer from the system or download it")
  }

  import autoImport._

  override def projectSettings = Seq(
    packerVersion := "0.7.5",
    packerSourceAmi := "ami-64e27e0c",
    packerInstanceType := "ubuntu",
    packerRegion := "us-east-1",
    packerInstanceType := "t1.micro",
    packerSshUsername := "ubuntu",
    packerAmiName <<= (name, version) { (n,v) =>  n + "-" + v + "-{{timestamp}}"},
    packerConfig := makePackerConfig(target.value, (packageBin in Debian).value, packerConfigTemplate.value, packerConfigTemplateReplacements.value),
    packerConfigTemplate := getPackerConfigTemplate,
    packerConfigTemplateReplacements <<= (packerAmiName, packerSourceAmi, packerInstanceType, packerRegion, packerSshUsername) {
      (amiName, sourceAmi, instanceType, region, sshUsername) => 
        Seq(
          "ami_name" → amiName,
          "region" → region,
          "ssh_username" → sshUsername,
          "source_ami" → sourceAmi,
          "instance_type" → instanceType
        )
    },
    packerCommand <<= (packerVersion, target, streams) map { (v, t, s) => checkInstalledPacker(v,s.log).getOrElse(getPacker(v, t, s.log)) },
    packerBuildAmi <<= (packerConfig, packerCommand) map { (conf, cmd) => buildAmi(conf,cmd) }
  )

  private[packer] def makePackerConfig(tmpDir: File, pkg:File, source: java.net.URL, replacements: Seq[(String, String)]): File = {
    val fileReplacements = Seq("source" → pkg.getAbsolutePath(),
        "destination" → ("/tmp/" + pkg.getName()))
    val fileContents = TemplateWriter.generateScript(source, fileReplacements ++ replacements)
    val nrFile = tmpDir / "tmp" / "packer.json"
    IO.write(nrFile, fileContents)
    nrFile
  }

  private[packer] def buildAmi(packerConf: File, packerCmd:String): Unit = {
    Process(Seq(packerCmd, "validate", packerConf.getAbsolutePath())).!
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
    val packerDir = (tmpDir / (packerBin + "_" + version))
    val packerFile = (packerDir / packerBin)
    if ( !packerFile.exists ) {
      val packerZip = "packer_" + version + "_linux_amd64.zip"
      val packerUrl = "https://dl.bintray.com/mitchellh/packer/" + packerZip
      log.info("Download Packer " + version + " at " + packerUrl)
      Process(Seq("wget", "-q", packerUrl, "-O", packerZip), Some(tmpDir)) #&&
        Process(Seq("unzip", "-d", packerDir.getAbsolutePath(), packerZip), Some(tmpDir)) ! log
    }
    else {
      log.info("Found previously downloaded packer in " + packerDir.getAbsolutePath())
    }
    packerFile.getAbsolutePath()
  }

  protected def getPackerConfigTemplate: java.net.URL = getClass.getResource("packer.json.template")

}
