package com.opticdev.server.http.routes.socket.editors

import akka.NotUsed
import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import com.opticdev.server.http.routes.socket.{Connection, ConnectionManager, OpticEvent}
import play.api.libs.json.{JsNumber, JsObject, JsString, Json}
import com.opticdev.server.http.routes.socket.editors.Protocol._
import com.opticdev.server.state.ProjectsManager

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Try}

class EditorConnection(slug: String, actorSystem: ActorSystem)(implicit projectsManager: ProjectsManager) extends Connection {

  private[this] val connectionActor = actorSystem.actorOf(Props(classOf[EditorConnectionActor], slug, projectsManager))

  def chatInSink(sender: String) = Sink.actorRef[EditorEvents](connectionActor, Terminated())

  def information : EditorConnection.EditorInformation = {
    implicit val timeout = Timeout(5 seconds)
    val future = connectionActor ? GetMetaInformation()
    Await.result(future, timeout.duration).asInstanceOf[EditorConnection.EditorInformation]
  }

  def sendUpdate(event: UpdateOpticEvent) = {
    connectionActor ! event
  }

  override def websocketFlow: Flow[String, OpticEvent, NotUsed] = {
    val in =
      Flow[String]
        .map(i=> {
          val parsedTry = Try(Json.parse(i).as[JsObject])
          val eventTry  = Try(parsedTry.get.value("event").as[JsString].value)
          val message = if (eventTry.isSuccess) {
            eventTry.get match {
              case "search" => {
                val queryTry  = Try(parsedTry.get.value("query").as[JsString].value)
                if (queryTry.isSuccess) Search(queryTry.get) else UnknownEvent(i)
              }
              case "updateMeta" => {
                val nameTry  = Try(parsedTry.get.value("name").as[JsString].value)
                val versionTry  = Try(parsedTry.get.value("version").as[JsString].value)
                if (nameTry.isSuccess && versionTry.isSuccess) UpdateMetaInformation(nameTry.get, versionTry.get) else UnknownEvent(i)
              }
              case "context" => {
                val fileTry = Try(parsedTry.get.value("file").as[JsString].value)
                val startTry = Try(parsedTry.get.value("start").as[JsNumber].value.toInt)
                val endTry = Try(parsedTry.get.value("end").as[JsNumber].value.toInt)
                val contentsTry = Try(parsedTry.get.value("contents").as[JsString].value).toOption

                if (fileTry.isSuccess && startTry.isSuccess && endTry.isSuccess)
                  Context(fileTry.get, Range(startTry.get, endTry.get), contentsTry) else UnknownEvent(i)
              }
              case _ => UnknownEvent(i)
            }
          } else UnknownEvent(i)

          message
        })
        .to(chatInSink(slug))

    val out =
      Source.actorRef[OpticEvent](1, OverflowStrategy.fail)
        .mapMaterializedValue(connectionActor ! Registered(_))

    Flow.fromSinkAndSource(in, out)
  }

  def poison = connectionActor ! PoisonPill

}

object EditorConnection extends ConnectionManager[EditorConnection] {
  case class EditorInformation(name: String, version: String)

  override def apply(slug: String)(implicit actorSystem: ActorSystem, projectsManager: ProjectsManager) = {
    println(slug+" editor connected")
    new EditorConnection(slug, actorSystem)
  }

  def killEditor(slug: String) = {
    println(slug+" editor disconnected")
    val connectionOption = connections.get(slug)
    if (connectionOption.isDefined) {
      connectionOption.get.poison
      connections -= slug
    }
  }
}

