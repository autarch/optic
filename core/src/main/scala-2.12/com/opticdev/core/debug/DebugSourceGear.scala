package com.opticdev.core.debug

import com.opticdev.common.PackageRef
import com.opticdev.core.sourcegear
import com.opticdev.core.sourcegear.project.{OpticProject, ProjectBase}
import com.opticdev.core.sourcegear.{FileParseResults, GearSet, SourceGear}
import com.opticdev.opm.{DependencyTree, PackageManager}
import com.opticdev.opm.packages.{OpticMDPackage, OpticPackage}
import com.opticdev.opm.providers.ProjectKnowledgeSearchPaths
import com.opticdev.parsers.{ParserBase, ParserResult}
import com.opticdev.parsers.graph.{CommonAstNode, GraphBuilder, WithinFile}
import com.opticdev.sdk.descriptions.{Lens, PackageExportable, Schema, SchemaRef}
import com.opticdev.sdk.descriptions.transformation.Transformation
import com.opticdev.sdk.markdown.MarkdownParser
import play.api.libs.json.{JsObject, JsString}
import OpticMDPackageRangeImplicits._
import com.opticdev.core.sourcegear.gears.parsing.ParseResult
import com.opticdev.core.sourcegear.graph.GraphOperations
import com.opticdev.core.sourcegear.graph.model.{LinkedModelNode, ModelNode}
import com.opticdev.core.sourcegear.project.config.ProjectFile

import scala.util.{Failure, Success, Try}

object DebugSourceGear extends SourceGear {

  override val parsers: Set[ParserBase] = Set()
  override val gearSet: GearSet = new GearSet()
  override val transformations: Set[Transformation] = Set()
  override val schemas: Set[Schema] = Set()

  //make sure to pass in the project context (if present) so OPM can tap into its knowledge paths
  override def parseString(string: String)(implicit project: ProjectBase): Try[sourcegear.FileParseResults] = Try {

    implicit val projectKnowledgeSearchPaths: ProjectKnowledgeSearchPaths =
      Try(project.asInstanceOf[OpticProject].projectFile.projectKnowledgeSearchPaths).getOrElse(ProjectKnowledgeSearchPaths())

    MarkdownParser.parseMarkdownString(string).map(result => {
      val dependenciesTry = Try(result.dependencies.value.map(i=> PackageRef.fromString(i.as[JsString].value).get))
      require(dependenciesTry.isSuccess, "Could not parse dependencies for this package "+ result.dependencies)
      val dependencies = dependenciesTry.get
      val dependencyTreeTry = PackageManager.collectPackages(dependencies)
      require(dependencyTreeTry.isSuccess, "Could not resolve dependencies "+ dependencyTreeTry.failed.get.toString)
      implicit val dependencyTree = dependencyTreeTry.get
      val dependencyMapping = dependencies.map(dep=> {
        (dep, dependencyTree.leafs.find(_.opticPackage.packageRef.packageId == dep.packageId).get.opticPackage.packageRef)
      }).toMap


      implicit val opticMDPackage = OpticPackage.fromJson(result.description).get.resolved(dependencyMapping)

      val schemaNodes = opticMDPackage.schemas.map(toAst).collect { case Some(n) => n }
      val lensNodes = opticMDPackage.lenses.map(toAst).collect { case Some(n) => n }
      val transformationNodes = opticMDPackage.transformations.map(toAst).collect { case Some(n) => n }

      val rootNode = CommonAstNode(DebugLanguageProxy.rootMarkdownNode, Range(0, string.length), JsObject.empty)

      val graphBuilder = new GraphBuilder[WithinFile]()
      val phase = graphBuilder.rootPhase.addChild(0, null, rootNode, false)

      schemaNodes.zipWithIndex.map { case (n, i) => phase.addChild(i, "schemas", n, true) }
      lensNodes.zipWithIndex.map { case (n, i) => phase.addChild(i, "lenses", n, true) }
      transformationNodes.zipWithIndex.map { case (n, i) => phase.addChild(i, "transformations", n, true) }

      implicit val astGraph = graphBuilder.graph

      def linkedModelNode[S <: PackageExportable](schemaRef: SchemaRef, node: DebugAstNode[S]): LinkedModelNode[DebugAstNode[S]] =
        LinkedModelNode(schemaRef, JsObject.empty, node, Map(), Map(), null)(project)

      val linkedModelNodes : Vector[LinkedModelNode[DebugAstNode[PackageExportable]]] = astGraph.nodes.toVector.map(_.value).collect {
        //for some reason the if is needed. likely type erasure
        case a: DebugAstNode[Schema] if a.nodeType == DebugLanguageProxy.schemaNode => linkedModelNode(DebugSchemaProxy.schemaNode, a)
        case a: DebugAstNode[Lens] if a.nodeType == DebugLanguageProxy.lensNode => linkedModelNode(DebugSchemaProxy.lensNode, a)
        case a: DebugAstNode[Transformation] if a.nodeType == DebugLanguageProxy.transformationNode => linkedModelNode(DebugSchemaProxy.transformationNode, a)
      }.asInstanceOf[Vector[LinkedModelNode[DebugAstNode[PackageExportable]]]]

      GraphOperations.addModelsToGraph(linkedModelNodes.map(i=> ParseResult(null, i, i.root)))

      val flat = linkedModelNodes.map(i=> i.flatten)

      FileParseResults(graphBuilder.graph, flat, null, string)
    })

  }.flatten

  def toAst(lens: Lens)(implicit opticMDPackage: OpticMDPackage, dependencyTree: DependencyTree) : Option[DebugAstNode[Lens]] = {
    opticMDPackage.rangeOfLens(lens).map(range=> {
      DebugAstNode(DebugLanguageProxy.lensNode, range, lens)
    })
  }

  def toAst(schema: Schema)(implicit opticMDPackage: OpticMDPackage, dependencyTree: DependencyTree) : Option[DebugAstNode[Schema]] = {
    opticMDPackage.rangeOfSchema(schema).map(range=> {
      DebugAstNode(DebugLanguageProxy.schemaNode, range, schema)
    })
  }

  def toAst(transformation: Transformation)(implicit opticMDPackage: OpticMDPackage, dependencyTree: DependencyTree) : Option[DebugAstNode[Transformation]] = {
    opticMDPackage.rangeOfTransformation(transformation).map(range=> {
      DebugAstNode(DebugLanguageProxy.transformationNode, range, transformation)
    })
  }

}