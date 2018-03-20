package com.opticdev.core.sourcegear.mutate

import com.opticdev.sdk.descriptions.CodeComponent
import com.opticdev.core.sourcegear.SGContext
import com.opticdev.core.sourcegear.graph.enums.AstPropertyRelationship
import com.opticdev.core.sourcegear.graph.model.{AstMapping, LinkedModelNode, NodeMapping, Path}
import com.opticdev.core.sourcegear.mutate.errors.{AstMappingNotFound, ComponentNotFound}
import com.opticdev.core.sourcegear.variables.{SetVariable, VariableChanges}
import com.opticdev.parsers.graph.path.PropertyPathWalker
import play.api.libs.json.{JsObject, JsString}
import gnieh.diffson.playJson._
import com.opticdev.core.utils.DiffOperationImplicits._
import com.opticdev.parsers.graph.CommonAstNode
import com.opticdev.parsers.graph.path.PropertyPathWalker
import com.opticdev.sdk.descriptions.enums.{Literal, ObjectLiteral, Token}
import com.opticdev.sdk.descriptions.transformation.VariableMapping

import scala.util.{Success, Try}

object MutationSteps {

  //require newValue to be a valid model.
  def collectFieldChanges(linkedModelNode: LinkedModelNode, newValue: JsObject): List[Try[UpdatedField]] = {
    //todo validate that newValue is a valid model
    val diff = JsonDiff.diff(linkedModelNode.value, newValue, true)
    val components = linkedModelNode.parseGear.components.flatMap(_._2).toSet

    diff.ops.map(change=> Try {
      val propertyPath = change.propertyPath

      val newFieldValue = new PropertyPathWalker(newValue).getProperty(propertyPath).get

      val component = components.find(_.propertyPath == propertyPath)
      if (component.isEmpty) throw new ComponentNotFound(propertyPath)

      val mapping = linkedModelNode.modelMapping.get(Path(propertyPath))
      if (mapping.isEmpty) throw new AstMappingNotFound(propertyPath)

      UpdatedField(component.get, change, mapping.get, newFieldValue)
    })
  }

  def collectVariableChanges(linkedModelNode: LinkedModelNode, variableChanges: VariableChanges) (implicit sourceGearContext: SGContext, fileContents: String) : List[AstChange] = {
    if (variableChanges.hasChanges) {
      val foundIdentifierNodes = sourceGearContext.astGraph.nodes.collect {
        case n if n.isASTType(variableChanges.identifierNodeDesc.nodeType) => n.value.asInstanceOf[CommonAstNode]
      }

      val groupedByName = foundIdentifierNodes.groupBy(n=> (n.properties \ variableChanges.identifierNodeDesc.path.head).get.as[JsString].value)

      variableChanges.changes.toList.flatMap(v=> {
        groupedByName.getOrElse(v.variable.token, Vector()).map(i=> {
          AstChange(NodeMapping(i, AstPropertyRelationship.Variable), Success(v.value))
        })
      })

    } else {
      List.empty
    }
  }

  def handleChanges(updatedFields: List[UpdatedField]) (implicit sourceGearContext: SGContext, fileContents: String): List[AstChange] = {
    updatedFields.map(field=> {
      field.component match {
        case i: CodeComponent => i.componentType match {
          case Literal => AstChange(field.mapping, mutateLiteral(field))
          case Token => AstChange(field.mapping, mutateToken(field))
          case ObjectLiteral => AstChange(field.mapping, mutateToken(field))
        }
      }
    })
  }

  def mutateLiteral(updatedField: UpdatedField) (implicit sourceGearContext: SGContext, fileContents: String): Try[String] = {
    val node = updatedField.mapping.asInstanceOf[NodeMapping].node
    import com.opticdev.core.utils.DiffOperationImplicits.DiffTypes._
    updatedField.diffOperation.changeType match {
      case REPLACE => sourceGearContext.parser.basicSourceInterface.literals.mutateNode(node, sourceGearContext.astGraph, node.raw, updatedField.newValue)
      case _ => throw new Error("Literals can only be replaced.")
    }
  }
  def mutateToken(updatedField: UpdatedField) (implicit sourceGearContext: SGContext, fileContents: String): Try[String] = {
    val node = updatedField.mapping.asInstanceOf[NodeMapping].node
    import com.opticdev.core.utils.DiffOperationImplicits.DiffTypes._
    updatedField.diffOperation.changeType match {
      case REPLACE => sourceGearContext.parser.basicSourceInterface.tokens.mutateNode(node, sourceGearContext.astGraph, node.raw, updatedField.newValue.as[JsString])
      case _ => throw new Error("Tokens can only be replaced.")
    }
  }

  def mutateObjectLiteral(updatedField: UpdatedField) (implicit sourceGearContext: SGContext, fileContents: String): Try[String] = {
    val node = updatedField.mapping.asInstanceOf[NodeMapping].node
    import com.opticdev.core.utils.DiffOperationImplicits.DiffTypes._
    updatedField.diffOperation.changeType match {
      case REPLACE => sourceGearContext.parser.basicSourceInterface.objectLiterals.mutateNode(node, sourceGearContext.astGraph, node.raw, updatedField.newValue.as[JsString])
      case _ => throw new Error("Object Literals can only be replaced.")
    }
  }

  def orderChanges(astChanges: List[AstChange]) = {
    astChanges.sortBy(change=> {
      change.mapping match {
        case NodeMapping(node, relationship) => node.range.end
        case _ => 0
      }
    }).reverse
  }

  def combineChanges(astChanges: List[AstChange]) (implicit sourceGearContext: SGContext, fileContents: String): StringBuilder = {
    val failedUpdates = astChanges.filter(_.replacementString.isFailure)
    import com.opticdev.core.utils.StringBuilderImplicits._
    val ordered = orderChanges(astChanges.filter(_.replacementString.isSuccess))
    ordered.foldLeft ( new StringBuilder(fileContents) ) {
      case (contents, change) => {
        change.mapping match {
          case NodeMapping(node, relationship) => contents.updateRange(node.range, change.replacementString.get)
        }
      }

    }
  }

}
