package org.llm4s.runner

/**
 * Simplified tool registry for workspace runner without external dependencies.
 */
case class SimpleToolCall(
  name: String,
  arguments: ujson.Value
)

case class SimpleToolResult(
  success: Boolean,
  result: ujson.Value,
  error: Option[String] = None
)

case class SimpleToolInfo(
  name: String,
  description: String,
  parameterSchema: ujson.Value
)

trait SimpleTool {
  def name: String
  def description: String
  def parameterSchema: ujson.Value
  def execute(arguments: ujson.Value): SimpleToolResult
}

class SimpleToolRegistry(tools: Seq[SimpleTool]) {

  def getTools: Seq[SimpleToolInfo] = tools.map { tool =>
    SimpleToolInfo(tool.name, tool.description, tool.parameterSchema)
  }

  def executeTool(toolCall: SimpleToolCall): SimpleToolResult =
    tools.find(_.name == toolCall.name) match {
      case Some(tool) =>
        try
          tool.execute(toolCall.arguments)
        catch {
          case ex: Exception =>
            SimpleToolResult(
              success = false,
              result = ujson.Str(""),
              error = Some(s"Tool execution failed: ${ex.getMessage}")
            )
        }
      case None =>
        SimpleToolResult(
          success = false,
          result = ujson.Str(""),
          error = Some(s"Unknown tool: ${toolCall.name}")
        )
    }
}
