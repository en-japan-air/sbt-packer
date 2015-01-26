package com.enjapan.sbt.packer

import sbt._
import sbt.Keys.{ packageBin, target, name, streams, version}

import com.typesafe.sbt.packager.archetypes.{JavaAppPackaging, TemplateWriter}
import com.typesafe.sbt.packager.debian.DebianPlugin.autoImport.Debian


object PackerPlugin extends AutoPlugin {

  override val requires = JavaAppPackaging

  object autoImport {
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
    packerCommand <<= (target, streams) map { (t, s) => getPacker(t, s.log) },
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

  private[packer] def getPacker(tmpDir:File, log:Logger):String = {
    "packer version" ! match {
      case 0 => "packer"
      case x => 
        Process(Seq("wget", "--no-check-certificate", "https://dl.bintray.com/mitchellh/packer/packer_0.7.5_linux_amd64.zip"), Some(tmpDir)) #&&
        Process(Seq("unzip", "-d", "packer", "packer_0.7.5_linux_amd64.zip"), Some(tmpDir)) ! log
        (tmpDir / "packer" / "packer").getAbsolutePath()
    }
  }

  protected def getPackerConfigTemplate: java.net.URL = getClass.getResource("packer.json.template")

}
