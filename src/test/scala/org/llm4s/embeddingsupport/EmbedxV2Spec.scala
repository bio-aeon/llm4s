package embeddingsupport

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import org.llm4s.llmconnect.encoding.UniversalEncoder
import org.llm4s.llmconnect.model._
import org.llm4s.llmconnect.provider.EmbeddingProvider
import org.llm4s.llmconnect.EmbeddingClient
import org.llm4s.llmconnect.utils.ModelSelector
import org.llm4s.llmconnect.config.{ EmbeddingConfig, ModelDimensionRegistry }
import org.llm4s.config.ConfigReader
import org.llm4s.config.ConfigReader.LLMConfig

import java.nio.file.{ Files, Path }
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

/**
 * Lean, high-signal tests for Embedx-v2.
 *
 * Notes:
 * - Tests that depend on ENABLE_EXPERIMENTAL_STUBS use runtime env. We skip them
 *   when the env isn't in the expected state to avoid false reds in CI.
 * - We never call real HTTP providers; a stub EmbeddingProvider is used.
 */
class EmbedxV2Spec extends AnyFunSuite with Matchers {

  // ----------------- Test scaffolding -----------------

  /** Stub provider: never hits network. */
  private val stubClient = new EmbeddingClient(new EmbeddingProvider {
    override def embed(request: EmbeddingRequest): Either[EmbeddingError, EmbeddingResponse] =
      Left(EmbeddingError(Some("418"), "stub provider (not used in these tests)", "stub"))
  })

  private def experimentalOn: Boolean =
    sys.env.get("ENABLE_EXPERIMENTAL_STUBS").exists(_.trim.equalsIgnoreCase("true"))

  private def withTempFile(prefix: String, suffix: String)(write: Path => Unit): Path = {
    val p = Files.createTempFile(prefix, suffix)
    try write(p)
    finally ()
    p.toFile.deleteOnExit()
    p
  }

  private def writeDummyPng(p: Path): Unit = {
    val img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
    img.setRGB(0, 0, 0x00ff00) // green pixel
    ImageIO.write(img, "png", p.toFile)
  }

  /** Minimal PCM WAV header (mono, 16-bit, 16kHz) with 0 data bytes. */
  private def writeDummyWav(p: Path): Unit = {
    val dataSize   = 0
    val byteRate   = 16000 * 1 * 16 / 8
    val blockAlign = 1 * 16 / 8
    val buf        = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    buf.put("RIFF".getBytes("US-ASCII"))
    buf.putInt(36 + dataSize) // ChunkSize
    buf.put("WAVE".getBytes("US-ASCII"))
    buf.put("fmt ".getBytes("US-ASCII"))
    buf.putInt(16)                   // Subchunk1Size
    buf.putShort(1.toShort)          // AudioFormat = PCM
    buf.putShort(1.toShort)          // NumChannels = 1
    buf.putInt(16000)                // SampleRate
    buf.putInt(byteRate)             // ByteRate
    buf.putShort(blockAlign.toShort) // BlockAlign
    buf.putShort(16.toShort)         // BitsPerSample
    buf.put("data".getBytes("US-ASCII"))
    buf.putInt(dataSize) // Subchunk2Size
    Files.write(p, buf.array())
  }

  /** Minimal MP4 'ftyp' box sufficient for Tika to detect video/mp4. */
  private def writeDummyMp4(p: Path): Unit = {
    val bb = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN)
    bb.putInt(24)                       // size
    bb.put("ftyp".getBytes("US-ASCII")) // type
    bb.put("isom".getBytes("US-ASCII")) // major brand
    bb.putInt(512)                      // minor version
    bb.put("isom".getBytes("US-ASCII")) // compatible brand 1
    bb.put("mp42".getBytes("US-ASCII")) // compatible brand 2
    Files.write(p, bb.array())
  }

  // ----------------- Tests -----------------

  test("Non-text: image/audio/video → 501 by default (stubs disabled)") {
    if (experimentalOn) cancel("ENABLE_EXPERIMENTAL_STUBS=true in env; skipping default-501 test.")

    val png = withTempFile("embedx_png_", ".png")(writeDummyPng)
    val wav = withTempFile("embedx_wav_", ".wav")(writeDummyWav)
    val mp4 = withTempFile("embedx_mp4_", ".mp4")(writeDummyMp4)

    val imgRes = UniversalEncoder.encodeFromPath(png, stubClient)
    val audRes = UniversalEncoder.encodeFromPath(wav, stubClient)
    val vidRes = UniversalEncoder.encodeFromPath(mp4, stubClient)

    imgRes.isLeft shouldBe true
    audRes.isLeft shouldBe true
    vidRes.isLeft shouldBe true

    imgRes.left.get.code shouldBe Some("501")
    audRes.left.get.code shouldBe Some("501")
    vidRes.left.get.code shouldBe Some("501")

    imgRes.left.get.provider shouldBe "encoder"
    audRes.left.get.provider shouldBe "encoder"
    vidRes.left.get.provider shouldBe "encoder"
  }

  test("Experimental stubs: image/audio/video produce vectors and experimental=true") {
    if (!experimentalOn) cancel("ENABLE_EXPERIMENTAL_STUBS!=true; set it to run this test.")

    val png = withTempFile("embedx_png_", ".png")(writeDummyPng)
    val wav = withTempFile("embedx_wav_", ".wav")(writeDummyWav)
    val mp4 = withTempFile("embedx_mp4_", ".mp4")(writeDummyMp4)

    val imgRes = UniversalEncoder.encodeFromPath(png, stubClient)
    val audRes = UniversalEncoder.encodeFromPath(wav, stubClient)
    val vidRes = UniversalEncoder.encodeFromPath(mp4, stubClient)

    val img = imgRes.toOption.get; img.nonEmpty shouldBe true
    val aud = audRes.toOption.get; aud.nonEmpty shouldBe true
    val vid = vidRes.toOption.get; vid.nonEmpty shouldBe true

    img.head.meta.get("experimental") shouldBe Some("true")
    aud.head.meta.get("experimental") shouldBe Some("true")
    vid.head.meta.get("experimental") shouldBe Some("true")

    img.head.meta.get("provider") shouldBe Some("local-experimental")
    aud.head.meta.get("provider") shouldBe Some("local-experimental")
    vid.head.meta.get("provider") shouldBe Some("local-experimental")
  }

  test("Local model dimensions (Image/Audio/Video) match registry") {
    val config   = LLMConfig()
    val imgModel = ModelSelector.selectModel(Image, config)
    val audModel = ModelSelector.selectModel(Audio, config)
    val vidModel = ModelSelector.selectModel(Video, config)

    imgModel.dimensions shouldBe ModelDimensionRegistry.getDimension("local", EmbeddingConfig.imageModel(config))
    audModel.dimensions shouldBe ModelDimensionRegistry.getDimension("local", EmbeddingConfig.audioModel(config))
    vidModel.dimensions shouldBe ModelDimensionRegistry.getDimension("local", EmbeddingConfig.videoModel(config))
  }

  test("EmbeddingResponse back-compat: vectors alias equals embeddings") {
    val resp = EmbeddingResponse(embeddings = Seq(Seq(0.1, 0.2, 0.3)))
    resp.vectors shouldBe resp.embeddings
  }
}
