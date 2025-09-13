package org.llm4s.toolapi

import scala.annotation.tailrec

/**
 * Safe parameter extraction with type checking and path navigation.
 *
 * This extractor provides two modes of operation:
 * 1. Simple mode: Returns Either[String, T] for backward compatibility
 * 2. Enhanced mode: Returns Either[ToolParameterError, T] for structured error reporting
 *
 * @param params The JSON parameters to extract from
 */
case class SafeParameterExtractor(params: ujson.Value) {
  // Simple mode - returns string errors for backward compatibility
  def getString(path: String): Either[String, String] =
    extract(path, _.strOpt, "string")

  def getInt(path: String): Either[String, Int] =
    extract(path, _.numOpt.map(_.toInt), "integer")

  def getDouble(path: String): Either[String, Double] =
    extract(path, _.numOpt, "number")

  def getBoolean(path: String): Either[String, Boolean] =
    extract(path, _.boolOpt, "boolean")

  def getArray(path: String): Either[String, ujson.Arr] =
    extract(path, v => Option(v).collect { case arr: ujson.Arr => arr }, "array")

  def getObject(path: String): Either[String, ujson.Obj] =
    extract(path, v => Option(v).collect { case obj: ujson.Obj => obj }, "object")

  // Enhanced mode - returns structured errors
  def getStringEnhanced(path: String): Either[ToolParameterError, String] =
    extractEnhanced(path, _.strOpt, "string")

  def getIntEnhanced(path: String): Either[ToolParameterError, Int] =
    extractEnhanced(path, _.numOpt.map(_.toInt), "integer")

  def getDoubleEnhanced(path: String): Either[ToolParameterError, Double] =
    extractEnhanced(path, _.numOpt, "number")

  def getBooleanEnhanced(path: String): Either[ToolParameterError, Boolean] =
    extractEnhanced(path, _.boolOpt, "boolean")

  def getArrayEnhanced(path: String): Either[ToolParameterError, ujson.Arr] =
    extractEnhanced(path, v => Option(v).collect { case arr: ujson.Arr => arr }, "array")

  def getObjectEnhanced(path: String): Either[ToolParameterError, ujson.Obj] =
    extractEnhanced(path, v => Option(v).collect { case obj: ujson.Obj => obj }, "object")

  // Optional parameter methods (enhanced mode only)
  def getOptionalString(path: String): Either[ToolParameterError, Option[String]] =
    extractOptional(path, _.strOpt, "string")

  def getOptionalInt(path: String): Either[ToolParameterError, Option[Int]] =
    extractOptional(path, _.numOpt.map(_.toInt), "integer")

  def getOptionalDouble(path: String): Either[ToolParameterError, Option[Double]] =
    extractOptional(path, _.numOpt, "number")

  def getOptionalBoolean(path: String): Either[ToolParameterError, Option[Boolean]] =
    extractOptional(path, _.boolOpt, "boolean")

  // Simple mode extractor - returns string errors
  private def extract[T](path: String, extractor: ujson.Value => Option[T], expectedType: String): Either[String, T] =
    extractEnhanced(path, extractor, expectedType).left.map(_.getMessage)

  // Enhanced mode extractor - returns structured errors
  private def extractEnhanced[T](
    path: String,
    extractor: ujson.Value => Option[T],
    expectedType: String
  ): Either[ToolParameterError, T] =
    try {
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
    } catch {
      case _: Exception =>
        Left(ToolParameterError.MissingParameter(path, expectedType, Nil))
    }

  // Optional parameter extraction
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

  // Navigate to a value in nested JSON
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
      extractEnhanced(path, _ => Some(()), expectedType) match {
        case Left(error) => Some(error)
        case Right(_)    => None
      }
    }.toList

    if (errors.isEmpty) Right(())
    else Left(errors)
  }
}

object SafeParameterExtractor {

  /**
   * Create an extractor that uses enhanced error reporting by default.
   * This is a convenience method for code that wants to use structured errors.
   */
  def enhanced(params: ujson.Value): SafeParameterExtractor = SafeParameterExtractor(params)
}
