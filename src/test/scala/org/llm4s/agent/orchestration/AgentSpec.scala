package org.llm4s.agent.orchestration

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.duration._
import scala.concurrent.{ Future, ExecutionContext }

/**
 * Unit tests for Agent abstraction following LLM4S testing patterns
 */
class AgentSpec extends AnyFlatSpec with Matchers with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "Agent.fromFunction" should "create a working functional agent" in {
    val agent = Agent.fromFunction[String, Int]("test-agent")(input => Right(input.length))

    agent.name shouldBe "test-agent"
<<<<<<< HEAD
    agent.id should not be null

    val result = agent.execute("hello").unsafeRunSync()
    result shouldBe Right(5)
=======
    agent.id.value should not be empty
<<<<<<< HEAD
    
    whenReady(agent.execute("hello")) { result =>
      result shouldBe Right(5)
    }
>>>>>>> f05d9ad (addressed the comments)
=======

    whenReady(agent.execute("hello"))(result => result shouldBe Right(5))
>>>>>>> a2539cb (test 1)
  }

  "Agent.fromUnsafeFunction" should "handle exceptions safely" in {
    val agent = Agent.fromUnsafeFunction[String, Int]("unsafe-agent") { input =>
      if (input.isEmpty) throw new RuntimeException("Empty input")
      else input.length
    }

    // Should succeed with valid input
<<<<<<< HEAD
<<<<<<< HEAD
    val successResult = agent.execute("hello").unsafeRunSync()
    successResult shouldBe Right(5)

=======
    whenReady(agent.execute("hello")) { result =>
      result shouldBe Right(5)
    }
    
>>>>>>> f05d9ad (addressed the comments)
=======
    whenReady(agent.execute("hello"))(result => result shouldBe Right(5))

>>>>>>> a2539cb (test 1)
    // Should handle exceptions
    whenReady(agent.execute(""))(result => result.isLeft shouldBe true)
  }

<<<<<<< HEAD
  "Agent.fromIO" should "handle IO operations properly" in {
    val agent = Agent.fromIO[String, String]("io-agent")(input => IO.pure(Right(s"processed: $input")))

    val result = agent.execute("test").unsafeRunSync()
    result shouldBe Right("processed: test")
=======
  "Agent.fromFuture" should "handle Future operations properly" in {
    val agent = Agent.fromFuture[String, String]("future-agent") { input =>
      Future.successful(Right(s"processed: $input"))
    }
<<<<<<< HEAD
    
    whenReady(agent.execute("test")) { result =>
      result shouldBe Right("processed: test")
    }
>>>>>>> f05d9ad (addressed the comments)
=======

    whenReady(agent.execute("test"))(result => result shouldBe Right("processed: test"))
>>>>>>> a2539cb (test 1)
  }

  "Agent.fromFuture" should "handle Future failures" in {
    val agent = Agent.fromFuture[String, String]("failing-future-agent") { _ =>
      Future.failed(new RuntimeException("Future failed"))
    }
<<<<<<< HEAD
<<<<<<< HEAD

    val result = agent.execute("test").unsafeRunSync()
    result.isLeft shouldBe true
    result.left.get shouldBe a[OrchestrationError.NodeExecutionError]
=======
    
=======

>>>>>>> a2539cb (test 1)
    whenReady(agent.execute("test")) { result =>
      result.isLeft shouldBe true
      result.swap
        .getOrElse(throw new RuntimeException("Expected Left"))
        .shouldBe(a[OrchestrationError.NodeExecutionError])
    }
>>>>>>> f05d9ad (addressed the comments)
  }

  "Agent.constant" should "always return the same value" in {
    val agent = Agent.constant[String, Int]("constant-agent", 42)
<<<<<<< HEAD
<<<<<<< HEAD

    agent.execute("anything").unsafeRunSync() shouldBe Right(42)
    agent.execute("different").unsafeRunSync() shouldBe Right(42)
=======
    
    whenReady(agent.execute("anything")) { result =>
      result shouldBe Right(42)
    }
    whenReady(agent.execute("different")) { result =>
      result shouldBe Right(42)
    }
>>>>>>> f05d9ad (addressed the comments)
=======

    whenReady(agent.execute("anything"))(result => result shouldBe Right(42))
    whenReady(agent.execute("different"))(result => result shouldBe Right(42))
>>>>>>> a2539cb (test 1)
  }

  "Agent.simpleFailure" should "always fail with the specified error" in {
    val agent = Agent.simpleFailure[String, Int]("failure-agent", "Always fails")
<<<<<<< HEAD
<<<<<<< HEAD

    val result = agent.execute("test").unsafeRunSync()
    result.isLeft shouldBe true
    result.left.get shouldBe a[OrchestrationError.NodeExecutionError]
=======
    
=======

>>>>>>> a2539cb (test 1)
    whenReady(agent.execute("test")) { result =>
      result.isLeft shouldBe true
      result.swap
        .getOrElse(throw new RuntimeException("Expected Left"))
        .shouldBe(a[OrchestrationError.NodeExecutionError])
    }
>>>>>>> f05d9ad (addressed the comments)
  }

  "Agent composition" should "work with different input/output types" in {
    val stringToInt = Agent.fromFunction[String, Int]("string-to-int")(s => Right(s.length))
    val intToString = Agent.fromFunction[Int, String]("int-to-string")(i => Right(s"Length: $i"))

    // Test sequential execution (manual composition for now)
    val input = "hello world"
<<<<<<< HEAD
    val step1 = stringToInt.execute(input).unsafeRunSync()
    step1 shouldBe Right(11)

    val step2 = intToString.execute(step1.getOrElse(0)).unsafeRunSync()
    step2 shouldBe Right("Length: 11")
=======
    whenReady(stringToInt.execute(input)) { step1 =>
      step1 shouldBe Right(11)

      whenReady(intToString.execute(step1.getOrElse(0)))(step2 => step2 shouldBe Right("Length: 11"))
    }
>>>>>>> f05d9ad (addressed the comments)
  }

  "Agent with long-running operation" should "complete eventually" in {
    val slowAgent = Agent.fromFuture[String, String]("slow-agent") { input =>
      Future {
        Thread.sleep(100) // Short delay for test
        Right(s"slow: $input")
      }
    }
<<<<<<< HEAD
<<<<<<< HEAD

    // Use TestControl for deterministic testing of time-based operations
    TestControl
      .executeEmbed {
        val execution = slowAgent.execute("test")
        for {
          fiber  <- execution.start
          _      <- IO.sleep(1.second)
          _      <- fiber.cancel
          result <- fiber.joinWithNever.attempt
        } yield result.isLeft shouldBe true // Should be cancelled
      }
      .unsafeRunSync()
=======
    
    whenReady(slowAgent.execute("test"), timeout(5.seconds)) { result =>
      result shouldBe Right("slow: test")
    }
>>>>>>> f05d9ad (addressed the comments)
=======

    whenReady(slowAgent.execute("test"), timeout(5.seconds))(result => result shouldBe Right("slow: test"))
>>>>>>> a2539cb (test 1)
  }
}
