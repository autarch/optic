package com.useoptic.ux

import com.useoptic.contexts.rfc.Commands.RfcCommand
import com.useoptic.contexts.rfc.{RfcService, RfcServiceJSFacade, RfcState}
import com.useoptic.contexts.shapes.ShapeEntity
import com.useoptic.diff.JsonFileFixture
import com.useoptic.diff.helpers.SpecHelpers
import com.useoptic.diff.interactions.TestHelpers
import com.useoptic.diff.shapes.{JsonLikeTraverser, JsonTrail, ShapeTrail}
import com.useoptic.diff.shapes.visitors.DiffVisitors
import com.useoptic.dsa.SequentialIdGenerator
import com.useoptic.end_to_end.fixtures.{JsonExamples, ShapeExamples}
import com.useoptic.types.capture.{ArbitraryData, Body, HttpInteraction, JsonLikeFrom, Request, Response}
import io.circe.Json
import org.scalatest.FunSpec

class DiffManagerSpec extends FunSpec with JsonFileFixture {

  val interactionIdGenerator = new SequentialIdGenerator("interaction")

  def withPathAndMethod(method: String, path: String, statusCode: Int) = {
    val interactionId = interactionIdGenerator.nextId()
    HttpInteraction(
      interactionId,
      Request("api.com", method, path, ArbitraryData.empty, ArbitraryData.empty, Body.empty),
      Response.emptyWithStatusCode(statusCode),
      Vector.empty
    )
  }

  it("can sort unmatched URLs / method") {

    val diffManager = new DiffManager(Seq(
      withPathAndMethod("post", "/todos", 200),
      withPathAndMethod("post", "/todos", 200),
      withPathAndMethod("post", "/todos", 200),
      withPathAndMethod("put", "/todos/todoId", 200),
      withPathAndMethod("get", "/todos/todoId", 200),
      withPathAndMethod("delete", "/todos/todoId", 404),
      withPathAndMethod("delete", "/todos/6543", 404),
    ))

    diffManager.updatedRfcState(RfcState.empty)

    val urls = diffManager.unmatchedUrls(false, Seq.empty)
    assert(urls.find(i => i.path == "/todos" && i.method == "post").get.interactions.size == 3)
    assert(urls.find(i => i.path == "/todos/todoId" && i.method == "put").get.interactions.size == 1)
    assert(urls.find(i => i.path == "/todos/todoId" && i.method == "get").get.interactions.size == 1)
    assert(!urls.exists(i => i.method == "delete"))
  }

  it("will give unmatched methods if a path is known") {
    val spec = TestHelpers.fromCommands(new SpecHelpers().simpleGet(Json.obj()))

    val diffManager = new DiffManager(Seq(
      withPathAndMethod("post", "/", 200)
    ))

    diffManager.updatedRfcState(spec)

    val urls = diffManager.unmatchedUrls(false, Seq.empty)
    assert(urls.head.path == "/")
    assert(urls.head.method == "post")
  }

  describe("Groupings are respected by endpoint") {
    def rfcStateFromEvents(e: String): RfcState = {
      val events = eventsFrom(e)
      val rfcId: String = "rfc-1"
      val eventStore = RfcServiceJSFacade.makeEventStore()
      eventStore.append(rfcId, events)
      val rfcService: RfcService = new RfcService(eventStore)
      rfcService.currentState(rfcId)
    }

    def shapeDiffsFor(shapeExample: (ShapeEntity, RfcState), observation: Json) = {

      val visitor = new DiffVisitors(shapeExample._2)
      val traverse = new JsonLikeTraverser(shapeExample._2, visitor)
      traverse.traverse(JsonLikeFrom.json(observation), JsonTrail(Seq.empty), Some(ShapeTrail(shapeExample._1.shapeId, Seq.empty)))
      visitor.diffs.toVector
    }

    it("can group diffs by shapetrail") {
      import com.useoptic.utilities.DistinctBy._
      val shapeDiffs = shapeDiffsFor(ShapeExamples.stringArray, JsonExamples.stringArrayWithNumbers)
      val grouped = shapeDiffs.distinctBy(i => i.shapeTrail)
      assert(grouped.size == 1)
    }

  }

}
