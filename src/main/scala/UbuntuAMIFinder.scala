package com.enjapan.sbt.packer

import sbt.IO
import java.net.URL

object UbuntuAMIFinder {
  
  def find(instanceType:String = "ebs", hvm:Boolean = false):Map[String, Map[String,String]] = {
    IO.readLinesURL(new URL("https://cloud-images.ubuntu.com/query/trusty/server/released.current.txt"))
    .map { _.split("\t")}
    .filter {x => x(4) == instanceType && x(10) == (if (hvm) "hvm" else "paravirtual")}
    .foldLeft(Map.empty[String,Map[String,String]]) { (mapping,x) => 
      val el = (x(6) -> (mapping.get(x(6)).getOrElse(Map.empty[String,String]) + (x(5) -> x(7))))
      mapping + el
    }
  }

}
