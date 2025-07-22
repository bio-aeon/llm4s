package $package; format = "package" $

/**
 * This code is part of the Giter8 template llm4s.g8 in llm4s project, which provides a set standard template/archetype
 * for improve developer onboarding, creating new projects using the llm4s library.
 */

/** The PromptExecutorSpec class contains unit tests for the PromptExecutor functionality.
 * It checks basic assertions and the response from the LLM when a prompt is executed.
 */

class PromptExecutorSpec extends munit.FunSuite {
  test("basic assertion") {
    assert(1 + 1 == 2)
  }

  test("Test for PromptExecutor") {
    val prompt = "Explain what a Monad is in Scala"
    val response = PromptExecutor.run(prompt)
    assert(response.nonEmpty, "Response should not be empty")
    assert(response.contains("Incorrect API key provided: your-api*****here. You can find your API key at https://platform.openai.com/account/api-keys."))
  }

  test("Integration Test: Real OpenAI call should return valid response") {
    sys.env.get("OPENAI_API_KEY") match {
      case Some(apiKey) =>
        val prompt = "Explain what a Monad is in Scala"
        val response = PromptExecutor.run(prompt)
        assert(response.nonEmpty, "Response should not be empty")
        assert(!response.contains("Incorrect API key provided: your-api*****here. You can find your API key at https://platform.openai.com/account/api-keys."))
      case None =>
        val prompt = "Explain what a Monad is in Scala"
        val response = PromptExecutor.run(prompt)
        assert(response.nonEmpty, "Response should not be empty")
        assert(response.contains("Incorrect API key provided: your-api*****here. You can find your API key at https://platform.openai.com/account/api-keys."))
    }
  }
}
