package org.llm4s.toolapi

import scala.annotation.tailrec

/**
 * Enhanced parameter extraction with structured error reporting
 */
case class EnhancedParameterExtractor(params: ujson.Value) {

  def getString(path: String): Either[ToolParameterError, String] =
    extract(path, _.strOpt, "string")

  def getInt(path: String): Either[ToolParameterError, Int] =
    extract(path, _.numOpt.map(_.toInt), "integer")

  def getDouble(path: String): Either[ToolParameterError, Double] =
    extract(path, _.numOpt, "number")

  def getBoolean(path: String): Either[ToolParameterError, Boolean] =
    extract(path, _.boolOpt, "boolean")

  def getArray(path: String): Either[ToolParameterError, ujson.Arr] =
    extract(path, v => Option(v).collect { case arr: ujson.Arr => arr }, "array")

  def getObject(path: String): Either[ToolParameterError, ujson.Obj] =
    extract(path, v => Option(v).collect { case obj: ujson.Obj => obj }, "object")

  // Get an optional parameter (returns None if missing, Left if wrong type)
  def getOptionalString(path: String): Either[ToolParameterError, Option[String]] =
    extractOptional(path, _.strOpt, "string")

  def getOptionalInt(path: String): Either[ToolParameterError, Option[Int]] =
    extractOptional(path, _.numOpt.map(_.toInt), "integer")

  def getOptionalDouble(path: String): Either[ToolParameterError, Option[Double]] =
    extractOptional(path, _.numOpt, "number")

  def getOptionalBoolean(path: String): Either[ToolParameterError, Option[Boolean]] =
    extractOptional(path, _.boolOpt, "boolean")

  private def extract[T](
    path: String,
    extractor: ujson.Value => Option[T],
    expectedType: String
  ): Either[ToolParameterError, T] = {
    val pathParts = if (path.contains('.')) path.split('.').toList else List(path)

    navigateToValue(pathParts, params) match {
      case Left(error) => Left(error)
      case Right(None) =>
        // Parameter is missing
        val availableParams = params match {
          case obj: ujson.Obj => obj.obj.keys.toList.sorted
          case _              => Nil
        }
        Left(ToolParameterError.MissingParameter(path, expectedType, availableParams))
      case Right(Some(ujson.Null)) =>
        // Parameter exists but is null
        Left(ToolParameterError.NullParameter(path, expectedType))
      case Right(Some(value)) =>
        // Try to extract the value with the correct type
        extractor(value) match {
          case Some(result) => Right(result)
          case None =>
            val actualType = getValueType(value)
            Left(ToolParameterError.TypeMismatch(path, expectedType, actualType))
        }
    }
  }

  private def extractOptional[T](
    path: String,
    extractor: ujson.Value => Option[T],
    expectedType: String
  ): Either[ToolParameterError, Option[T]] = {
    val pathParts = if (path.contains('.')) path.split('.').toList else List(path)

    navigateToValue(pathParts, params) match {
      case Left(error)             => Left(error)
      case Right(None)             => Right(None) // Optional parameter missing is OK
      case Right(Some(ujson.Null)) => Right(None) // Null for optional is OK
      case Right(Some(value)) =>
        extractor(value) match {
          case Some(result) => Right(Some(result))
          case None =>
            val actualType = getValueType(value)
            Left(ToolParameterError.TypeMismatch(path, expectedType, actualType))
        }
    }
  }

  private def navigateToValue(
    pathParts: List[String],
    current: ujson.Value
  ): Either[ToolParameterError, Option[ujson.Value]] = {

    @tailrec
    def navigate(
      parts: List[String],
      value: ujson.Value,
      traversedPath: List[String]
    ): Either[ToolParameterError, Option[ujson.Value]] =
      parts match {
        case Nil => Right(Some(value))
        case head :: tail =>
          value match {
            case ujson.Null =>
              val parentPath = traversedPath.mkString(".")
              Left(
                ToolParameterError.InvalidNesting(
                  head,
                  if (parentPath.isEmpty) "root" else parentPath,
                  "null"
                )
              )
            case obj: ujson.Obj =>
              obj.obj.get(head) match {
                case Some(nextValue) =>
                  navigate(tail, nextValue, traversedPath :+ head)
                case None =>
                  if (tail.isEmpty) {
                    // This is the final parameter we're looking for
                    Right(None)
                  } else {
                    // We're trying to navigate deeper but intermediate path is missing
                    val fullPath = (traversedPath :+ head).mkString(".")
                    Left(
                      ToolParameterError.MissingParameter(
                        fullPath,
                        "object",
                        obj.obj.keys.toList.sorted
                      )
                    )
                  }
              }
            case other =>
              val parentPath = traversedPath.mkString(".")
              Left(
                ToolParameterError.InvalidNesting(
                  head,
                  if (parentPath.isEmpty) "root" else parentPath,
                  getValueType(other)
                )
              )
          }
      }

    current match {
      case ujson.Null if pathParts.nonEmpty =>
        Left(
          ToolParameterError.InvalidNesting(
            pathParts.head,
            "root",
            "null"
          )
        )
      case _ =>
        navigate(pathParts, current, Nil)
    }
  }

  private def getValueType(value: ujson.Value): String = value match {
    case _: ujson.Str  => "string"
    case _: ujson.Num  => "number"
    case _: ujson.Bool => "boolean"
    case _: ujson.Arr  => "array"
    case _: ujson.Obj  => "object"
    case ujson.Null    => "null"
    case null          => "unknown"
  }

  /**
   * Validate all required parameters at once and collect errors
   */
  def validateRequired(
    requirements: (String, String)*
  ): Either[List[ToolParameterError], Unit] = {
    val errors = requirements.flatMap { case (path, expectedType) =>
      extract(path, _ => Some(()), expectedType) match {
        case Left(error) => Some(error)
        case Right(_)    => None
      }
    }.toList

    if (errors.isEmpty) Right(())
    else Left(errors)
  }
}
