package org.llm4s.toolapi

import scala.annotation.tailrec

/**
 * Safe parameter extraction with type checking and path navigation
 */
case class SafeParameterExtractor(params: ujson.Value) {
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

  // Generic extractor with type validation - no more boundary usage
  private def extract[T](path: String, extractor: ujson.Value => Option[T], expectedType: String): Either[String, T] =
    try {
      val pathParts = path.split('.')

      // Navigate to the value using a recursive approach instead of boundary
      @tailrec
      def navigatePath(current: ujson.Value, remainingParts: List[String]): Either[String, ujson.Value] =
        if (remainingParts.isEmpty) {
          Right(current)
        } else {
          val part = remainingParts.head
          current match {
            case ujson.Null =>
              val traversedPath = pathParts.dropRight(remainingParts.size).mkString(".")
              val context       = if (traversedPath.isEmpty) "root" else s"'$traversedPath'"
              Left(s"Cannot access property '$part' on null value at $context")
            case obj: ujson.Obj =>
              obj.obj.get(part) match {
                case Some(value) => navigatePath(value, remainingParts.tail)
                case None =>
                  val availableKeys = obj.obj.keys.toList.sorted
                  val keyInfo = if (availableKeys.nonEmpty) {
                    s" Available properties: ${availableKeys.mkString(", ")}"
                  } else {
                    " (object has no properties)"
                  }
                  Left(s"Required parameter '$part' is missing at path '$path'.$keyInfo")
              }
            case other =>
              val traversedPath = pathParts.dropRight(remainingParts.size).mkString(".")
              val actualType = other match {
                case _: ujson.Str  => "string"
                case _: ujson.Num  => "number"
                case _: ujson.Bool => "boolean"
                case _: ujson.Arr  => "array"
                case _             => other.getClass.getSimpleName
              }
              Left(
                s"Cannot access property '$part' on $actualType value at '${traversedPath}'. Expected an object but got $actualType."
              )
          }
        }

      // Get the value at the path
      navigatePath(params, pathParts.toList).flatMap { value =>
        value match {
          case ujson.Null =>
            Left(s"Parameter '$path' is null but expected $expectedType")
          case _ =>
            extractor(value) match {
              case Some(result) => Right(result)
              case None =>
                val actualType = value match {
                  case _: ujson.Str  => "string"
                  case _: ujson.Num  => "number"
                  case _: ujson.Bool => "boolean"
                  case _: ujson.Arr  => "array"
                  case _: ujson.Obj  => "object"
                  case _             => value.getClass.getSimpleName
                }
                Left(s"Type mismatch for parameter '$path': expected $expectedType but got $actualType")
            }
        }
      }
    } catch {
      case e: Exception => Left(s"Error extracting parameter at '$path': ${e.getMessage}")
    }
}
