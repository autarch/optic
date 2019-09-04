package com.seamless.diff

import io.circe.Json
import io.circe.jawn.parseFile
import java.io.File

import com.seamless.contexts.rfc.Commands
import com.seamless.serialization.CommandSerialization

import scala.util.Try

trait JsonFileFixture {
  def fromFile(slug: String): Json = {
    val filePath = "src/test/resources/diff-scenarios/" + slug + ".json"
    parseFile(new File(filePath)).right.get
  }
  def commandsFrom(slug: String): Vector[Commands.RfcCommand] = {
    val filePath = "src/test/resources/diff-scenarios/" + slug + ".commands.json"
    val json = parseFile(new File(filePath)).right.get
    CommandSerialization.fromJson(json).get
  }
}