package com.opticdev.core.sourcegear.mutate

import com.opticdev.core.sdk.descriptions.CodeComponent
import com.opticdev.core.sdk.descriptions.enums.ComponentEnums.{Literal, Token}
import com.opticdev.core.sourcegear.SourceGearContext
import com.opticdev.core.sourcegear.graph.model.{AstMapping, LinkedModelNode, NodeMapping, Path}
import com.opticdev.core.sourcegear.mutate.errors.{AstMappingNotFound, ComponentNotFound}
import com.opticdev.parsers.graph.path.PropertyPathWalker
import play.api.libs.json.{JsObject, JsString}
import gnieh.diffson.playJson._
import com.opticdev.core.utils.DiffOperationImplicits._
import com.opticdev.parsers.graph.path.PropertyPathWalker

import scala.util.Try

object MutationSteps {

  def collectChanges(linkedModelNode: LinkedModelNode, newValue: JsObject): List[UpdatedField] = {
    //todo validate that newValue is a valid model
    val diff = JsonDiff.diff(linkedModelNode.value, newValue, true)
    val components = linkedModelNode.parseGear.components.flatMap(_._2).toSet

    diff.ops.map(change=> {
      val propertyPath = change.propertyPath

      val newFieldValue = new PropertyPathWalker(newValue).getProperty(propertyPath).get

      val component = components.find(_.propertyPath == propertyPath)
      if (component.isEmpty) throw new ComponentNotFound(propertyPath)

      val mapping = linkedModelNode.mapping.get(Path(propertyPath))
      if (mapping.isEmpty) throw new AstMappingNotFound(propertyPath)

      UpdatedField(component.get, change, mapping.get, newFieldValue)
    })
  }

  def handleChanges(updatedFields: List[UpdatedField]) (implicit sourceGearContext: SourceGearContext): List[AstChange] = {
    updatedFields.map(field=> {
      field.component match {
        case i: CodeComponent => i.codeType match {
          case Literal => AstChange(field.mapping, mutateLiteral(field))
          case Token => AstChange(field.mapping, mutateToken(field))
        }
      }
    })
  }

  def mutateLiteral(updatedField: UpdatedField) (implicit sourceGearContext: SourceGearContext) = {
    val node = updatedField.mapping.asInstanceOf[NodeMapping].node
    import com.opticdev.core.utils.DiffOperationImplicits.DiffTypes._
    updatedField.diffOperation.changeType match {
      case REPLACE => sourceGearContext.parser.basicSourceInterface.literals.mutateNode(node, "", updatedField.newValue)
      case _ => throw new Error("Literals can only be replaced.")
    }
  }
  def mutateToken(updatedField: UpdatedField) (implicit sourceGearContext: SourceGearContext) = {
    val node = updatedField.mapping.asInstanceOf[NodeMapping].node
    import com.opticdev.core.utils.DiffOperationImplicits.DiffTypes._
    updatedField.diffOperation.changeType match {
      case REPLACE => sourceGearContext.parser.basicSourceInterface.tokens.mutateNode(node, "", updatedField.newValue.as[JsString])
      case _ => throw new Error("Literals can only be replaced.")
    }
  }

  def orderChanges(astChanges: List[AstChange]) = {
    astChanges.sortBy(change=> {
      change.mapping match {
        case NodeMapping(node, relationship) => node.range
        case _ => (0,0)
      }
    })
  }

  def combineChanges(astChanges: List[AstChange]) (implicit sourceGearContext: SourceGearContext, fileContents: String) = {
    val failedUpdates = astChanges.filter(_.replacementString.isFailure)

    val ordered = orderChanges(astChanges.filter(_.replacementString.isSuccess))
    ordered.foldLeft (fileContents) {
      case (contents, change) => {
        change.mapping match {
          case NodeMapping(node, relationship) => contents
        }
      }

    }

  }

}
