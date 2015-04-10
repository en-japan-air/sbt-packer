# sbt-packer
Sbt plugin for Packer.io

*Branch*|*Build Status*|
|---|---|
|*master*|[![Build Status](https://travis-ci.org/en-japan/sbt-packer.svg)](https://travis-ci.org/en-japan/sbt-packer)|

Still in a work in progress since it only supports Debian/Ubuntu types of
instances.

## Prerequisites
The plugin assumes that sbt-native-packager 1.0.0-M4 has been included in
your SBT build configuration, and its settings have been
initialized. This can by done by adding the plugin following instructions at
http://www.scala-sbt.org/sbt-native-packager/.

## Installation

Add the following to your `project/plugins.sbt` file:
```scala
resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
   url("https://dl.bintray.com/en-japan/sbt"))(
       Resolver.ivyStylePatterns)

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0-M4")

addSbtPlugin("com.en-japan" % "sbt-packer" % "0.0.7")
```

Add the following to your build.sbt
```scala
enablePlugins(JDebPackaging, PackerPlugin)
```

If you want to use native packager tools you should have installed (cf
[sbt-native-packager](http://www.scala-sbt.org/sbt-native-packager/formats/debian.html#requirements):
- dpkg-deb
- dpkg-sig
- dpkg-genchanges
- lintian
- fakeroot

Then you can use
```scala
enablePlugins(PackerPlugin)

// With native dpkg you also need to specify a maintainer for the deb package.
maintainer := "John Smith <john@smith.com>"
```

## Usage

Have your AWS credentials in your environment variables (`AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`) or in `~/.aws/credentials`)

First you can validate your build
```shell
sbt packerValidateConf
```

Use the command
```shell
sbt packerBuild
```
This will 
- will check if you have Packer installed with the required version (default:
  0.7.5) and download it you don't
- launch the `debian:packageBin` task from `sbt-native-package` to create a `.deb` package
- install it on amazon instance created from the AMI specified in packerSourceAmi (default: ubuntu see [Configuration](#Configuration))

## Play application
If you create an AMI with a Play application, the application will fail on
start since it will try to write its PID file in the application folder where
it has no write permission.

Fix: Add `-Dpidfile.path=/var/run/${{app_name}}/play.pid` to `src/templates/etc-default`
(cf [sbt/sbt-native-packager#241](https://github.com/sbt/sbt-native-packager/issues/241#issuecomment-42141586))

## Configuration

```scala
// Specify the version of Packer you want to use
packerVersion := "0.7.5"

// Specify the id of the source ami to generate from (defaults to current trusty ubuntu amd64 ebs AMI)
packerSourceAmi := "ami-64e27e0c"

// Specify AWS region in which the instance from which the AMI will be created will be run
packerRegion := "us-east-1"

// List of regions to copy the AMI to (defaults Set())
packerAmiRegions := Set("us-west-2", "ap-northeast-1")

// Specify the instance type
packerInstanceType := "t1.micro"

// Specify the name of the user to ssh with
packerSshUsername := "ubuntu"

// Specify the name of the generated ami (defaults to <name>-<version>-{{timestamp}})
packerAmiName := "super-ami"

// Specify some tags to add to the created AMI (defaults to Map.empty[String,String])
packerAmiTags := Map("name" -> "AwesomeAMI")
```

### Going further

You can write your own builders and provisioners too!

```scala
import com.enjapan.sbt.packer.Packer._

packerProvisioners += ShellProvisioner(Seq("echo Hello World!))

packerBuilders += VirtualBoxBuilder(
    guestOsType = "Ubuntu_64",
    isoUrl = "http://releases.ubuntu.com/12.04/ubuntu-12.04.5-server-amd64.iso",
    isoChecksum = "769474248a3897f4865817446f9a4a53",
    isoChecksumType = "md5",
    sshUsername = "packer",
    sshPassword = "packer",
    sshWaitTimeout = "30s",
    shutdownCommand = "echo 'packer' | sudo -S shutdown -P now"
)
```

### Old way

To use the previous template way

```scala
packerConfigFile <<= packerConfigFileOld
```


## License
Copyright 2015 en japan, Inc.

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
