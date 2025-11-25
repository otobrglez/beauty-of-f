import java.io.*
import java.nio.file.{Files, Path}
import javax.sound.sampled.*

object Config:
  val sampleRate          = 44100f
  val freq                = 600f      // Hz
  val wpm                 = 12f
  val unit                = 1.2 / wpm // seconds
  val volume              = 0.5f
  val units @ (dot, dash) = (1, 3)
  val gapIntra            = 1         // Gap between elements (dots and dashes)
  val gapLetters          = 3         // Gap between letters
  val gapWords            = 7         // Gap between words

def generatePCM(input: String): Array[Byte] =
  val out = new ByteArrayOutputStream()

  writeSilence(out, Config.gapIntra * 2) // Initial space
  morseEncode(input).foreach:
    case Some(token) =>
      val len = token.length
      token.zipWithIndex.foreach: (char, index) =>
        writeTone(out, units = if char == '.' then Config.dot else Config.dash)
        if index < len - 1 then writeSilence(out, Config.gapIntra)

      writeSilence(out, Config.gapLetters)

    case None =>
      writeSilence(out, units = Config.gapWords - Config.gapLetters)
  out.toByteArray

def writeTone(os: OutputStream, units: Int): Unit =
  val totalSamples = (Config.sampleRate * units * Config.unit).toInt
  (0 until totalSamples).foreach: i =>
    val t      = i.toFloat / Config.sampleRate
    val volume = Config.volume * Math.sin(2.0 * Math.PI * Config.freq * t).toFloat
    val sample = (volume * Short.MaxValue).toShort
    os.write(sample & 0xff)
    os.write((sample >> 8) & 0xff)

def writeSilence(os: OutputStream, units: Int): Unit =
  val totalSamples = (Config.sampleRate * units * Config.unit).toInt
  (0 until totalSamples).foreach: _ =>
    os.write(0); os.write(0)

def writePCM(pcm: Array[Byte], path: Path) =
  Files.deleteIfExists(path)

  val format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, Config.sampleRate, 16, 1, 2, Config.sampleRate, false)
  val bim    = new ByteArrayInputStream(pcm)
  val ais    = new AudioInputStream(bim, format, pcm.length / format.getFrameSize)

  AudioSystem.write(ais, AudioFileFormat.Type.WAVE, path.toFile)

@main def morseEncodeToSoundApp(input: String, output: String = "output.waw"): Unit =
  val outputFile = Path.of(output)
  println(s"INPUT: $input")
  println(s"OUTPUT: ${morseEncode(input).map(_.getOrElse(" ")).mkString(" ")}")
  println(s"OUTPUT FILE: ${outputFile.toAbsolutePath}")

  val pcm = generatePCM(input)
  writePCM(pcm, outputFile)
