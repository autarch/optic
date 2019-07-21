package com.seamless.contexts.shapes

import com.seamless.contexts.shapes.Commands._

object Validators {


  ////////////////////////////////////////////////////////////////////////////////

  def ensureFieldIdExists(fieldId: FieldId)(implicit state: ShapesState) = {
    require(state.fields.contains(fieldId))
  }

  def ensureFieldIdAssignable(fieldId: FieldId)(implicit state: ShapesState) = {
    require(!state.fields.contains(fieldId))
  }

  def ensureShapeIdCanAddField(shapeId: ShapeId)(implicit state: ShapesState) = {
    val baseShape = state.resolveCoreShapeId(shapeId)
    require(baseShape == "$object")
  }

  ////////////////////////////////////////////////////////////////////////////////

  def ensureShapeIdIsParentOfParameterId(shapeId: ShapeId, shapeParameterId: ShapeParameterId)(implicit state: ShapesState) = {
    val parameter = state.shapeParameters(shapeParameterId)
    require(shapeId == parameter.descriptor.shapeId)
  }

  ////////////////////////////////////////////////////////////////////////////////

  def ensureShapeParameterIdExistsForShapeId(shapeId: ShapeId, shapeParameterId: ShapeParameterId)(implicit state: ShapesState) = {
    val shape = state.shapes(shapeId)
    val existsInWrapper = shape.descriptor.parameters match {
      case NoParameterList() => false
      case StaticParameterList(ids) => ids.contains(shapeParameterId)
      case DynamicParameterList(ids) => ids.contains(shapeParameterId)
    }
    if (!existsInWrapper) {
      val wrappedShape = state.shapes(shape.descriptor.baseShapeId)
      val existsInWrapped = wrappedShape.descriptor.parameters match {
        case NoParameterList() => false
        case StaticParameterList(ids) => ids.contains(shapeParameterId)
        case DynamicParameterList(ids) => ids.contains(shapeParameterId)
      }
      require(existsInWrapped, s"parameter ${shapeParameterId} should be a parameter of user-defined shape ${shapeId}")
    } else {
      require(existsInWrapper, s"parameter ${shapeParameterId} should be a parameter of core shape ${shapeId}")
    }
  }

  ////////////////////////////////////////////////////////////////////////////////

  def ensureShapeIdExists(shapeId: ShapeId)(implicit state: ShapesState) = {
    require(state.shapes.contains(shapeId), s"Shape ID ${shapeId} must exist")
  }

  def ensureShapeIdAssignable(shapeId: ShapeId)(implicit state: ShapesState) = {
    require(!state.shapes.contains(shapeId))
  }

  ////////////////////////////////////////////////////////////////////////////////

  def ensureShapeParameterIdExists(shapeParameterId: ShapeParameterId)(implicit state: ShapesState) = {
    require(state.shapeParameters.contains(shapeParameterId))
  }

  def ensureShapeParameterIdAssignable(shapeParameterId: ShapeParameterId)(implicit state: ShapesState) = {
    require(!state.shapeParameters.contains(shapeParameterId))
  }

  def ensureParametersCanBeChanged(shapeId: ShapeId)(implicit state: ShapesState) = {
    val shape = state.shapes(shapeId)
    require(shape.descriptor.parameters.isInstanceOf[DynamicParameterList])
  }

  def ensureParameterCanBeRemoved(shapeParameterId: ShapeParameterId)(implicit state: ShapesState) = {
    val parameter = state.shapeParameters(shapeParameterId)
    val shape = state.shapes(parameter.descriptor.shapeId)
    require(shape.descriptor.parameters.isInstanceOf[DynamicParameterList])
  }

  ////////////////////////////////////////////////////////////////////////////////
}
