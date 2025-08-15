package org.llm4s.toolapi.tools

import org.llm4s.toolapi._
import upickle.default._

/**
 * Simple calculator tool for demonstrating LLM4S agent capabilities
 */
object CalculatorTool {
  
  // Define result type
  case class CalculationResult(
    operation: String,
    a: Double,
    b: Option[Double],
    result: Double,
    expression: String
  )

  // Provide implicit reader/writer
  implicit val calculationResultRW: ReadWriter[CalculationResult] = macroRW

  // Define calculator parameter schema
  val calculatorParamsSchema: ObjectSchema[Map[String, Any]] = Schema
    .`object`[Map[String, Any]]("Calculator request parameters")
    .withProperty(
      Schema.property(
        "operation",
        Schema
          .string("The mathematical operation to perform")
          .withEnum(Seq("add", "subtract", "multiply", "divide", "power", "sqrt"))
      )
    )
    .withProperty(
      Schema.property(
        "a",
        Schema.number("First number for the operation")
      )
    )
    .withProperty(
      Schema.property(
        "b",
        Schema.number("Second number for the operation (not needed for sqrt)")
      )
    )

  // Define type-safe handler function
  def calculatorHandler(params: SafeParameterExtractor): Either[String, CalculationResult] = {
    // Get required parameters
    val operation = params.getString("operation")
    val a = params.getDouble("a")
    
    // Try to get optional parameter b, but don't fail if it's missing
    val b = params.getDouble("b").toOption
    
    for {
      op <- operation
      numA <- a
      result <- calculateResult(op, numA, b)
    } yield {
      CalculationResult(
        operation = op,
        a = numA,
        b = b,
        result = result._1,
        expression = result._2
      )
    }
  }
  
  // Helper function to calculate result without non-local returns
  private def calculateResult(operation: String, a: Double, b: Option[Double]): Either[String, (Double, String)] = {
    operation match {
      case "add" => 
        b match {
          case Some(numB) => Right((a + numB, s"$a + $numB"))
          case None => Left("Second number 'b' is required for addition")
        }
      case "subtract" => 
        b match {
          case Some(numB) => Right((a - numB, s"$a - $numB"))
          case None => Left("Second number 'b' is required for subtraction")
        }
      case "multiply" => 
        b match {
          case Some(numB) => Right((a * numB, s"$a × $numB"))
          case None => Left("Second number 'b' is required for multiplication")
        }
      case "divide" => 
        b match {
          case Some(numB) => 
            if (numB == 0) Left("Division by zero is not allowed")
            else Right((a / numB, s"$a ÷ $numB"))
          case None => Left("Second number 'b' is required for division")
        }
      case "power" => 
        b match {
          case Some(numB) => Right((math.pow(a, numB), s"$a^$numB"))
          case None => Left("Second number 'b' is required for power operation")
        }
      case "sqrt" => 
        if (a < 0) Left("Cannot calculate square root of negative number")
        else Right((math.sqrt(a), s"√$a"))
      case _ => 
        Left(s"Unknown operation: $operation")
    }
  }

  // Build the calculator tool
  val tool = ToolBuilder[Map[String, Any], CalculationResult](
    "calculator",
    "Performs basic mathematical calculations including addition, subtraction, multiplication, division, and power operations",
    calculatorParamsSchema
  ).withHandler(calculatorHandler).build()
}
