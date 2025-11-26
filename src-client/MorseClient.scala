//> using platform scala-js
//> using dep org.scala-js::scalajs-dom::2.8.1
//> using option -Wunused:all

import org.scalajs.dom
import org.scalajs.dom.{console, document, window}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Date

object MorseClient:
  def main(args: Array[String]): Unit =
    document.addEventListener(
      "DOMContentLoaded",
      (_: dom.Event) =>
        Option(document.getElementById("listen")).foreach(
          _.addEventListener(
            "click",
            (e: dom.Event) =>
              e.preventDefault()
              startListening()
          )
        )
    )

  // Configuration for Human Input
  val Wpm      = 10.0
  val UnitTime = 1.2 / Wpm // ~0.12s

  // Thresholds
  // Short noise filter: < 0.5 unit (very short clicks ignored)
  val NoiseThreshold = UnitTime * 0.5

  // Dot vs Dash:
  // Dot should be ~1 unit. Dash ~3 units.
  // Threshold at 2.0 units.
  val DotDashThreshold = UnitTime * 2.0

  // Letter Gap:
  // Standard is 3 units. We use 3.5 to allow slow intra-char spacing.
  val LetterGapThreshold = UnitTime * 3.5

  // Word Gap:
  val WordGapThreshold = UnitTime * 7.0

  val TargetFreq = 600
  val Threshold  = 0.03

  // State
  var isListening                         = false
  var audioContext: dom.AudioContext      = null
  var analyser: dom.AnalyserNode          = null
  var dataArray: js.typedarray.Uint8Array = null
  var isSignalOn                          = false
  var lastChangeTime                      = 0.0
  var currentSymbol                       = ""
  var lastDecodedSpace                    = false

  def startListening(): Unit =
    if isListening then return
    isListening = true

    val constraints = new dom.MediaStreamConstraints:
      audio = true

    window.navigator.mediaDevices
      .getUserMedia(constraints)
      .toFuture
      .map { stream =>
        audioContext = new dom.AudioContext()
        val source = audioContext.createMediaStreamSource(stream)

        val filter = audioContext.createBiquadFilter()
        filter.`type` = "bandpass"
        filter.frequency.value = TargetFreq
        filter.Q.value = 10

        analyser = audioContext.createAnalyser()
        analyser.fftSize = 512
        analyser.smoothingTimeConstant = 0.2

        source.connect(filter)
        filter.connect(analyser)

        dataArray = new js.typedarray.Uint8Array(analyser.frequencyBinCount)

        lastChangeTime = Date.now()

        val state = document.getElementById("state")
        if state != null then state.textContent = "Listening... (Whistle 600Hz)"

        loop()
      }
      .failed
      .foreach(err => console.error("Error accessing microphone:", err))

  def loop(): Unit =
    if !isListening then return
    window.requestAnimationFrame(_ => loop())

    analyser.getByteTimeDomainData(dataArray)

    var sum = 0.0
    for i <- 0 until dataArray.length do
      val v = (dataArray(i) - 128) / 128.0
      sum += v * v
    val rms = Math.sqrt(sum / dataArray.length)

    val signalNow = rms > Threshold
    val now       = Date.now()
    val duration  = (now - lastChangeTime) / 1000.0

    if signalNow != isSignalOn then
      // State Changed
      if isSignalOn then
        // ON -> OFF (Tone ended)
        if duration < NoiseThreshold then
          // Too short, likely noise. Ignore this signal entirely.
          // But we must be careful: we just treated a 'blip' as signal.
          // Ideally we should rollback state, but here we just ignore adding symbol.
          console.log(s"Noise ignored: ${duration}s")
        else if duration < DotDashThreshold then
          currentSymbol += "."
          updateDebug(currentSymbol)
        else
          currentSymbol += "-"
          updateDebug(currentSymbol)
      else
        // OFF -> ON (Silence ended)
        lastDecodedSpace = false

      isSignalOn = signalNow
      lastChangeTime = now
    else
      // No state change
      if !isSignalOn then
        // Silence duration check
        if currentSymbol.nonEmpty && duration > LetterGapThreshold then
          decodeCurrentSymbol()
        // Do NOT reset lastChangeTime. Silence continues.

        if !lastDecodedSpace && duration > WordGapThreshold then
          appendOutput(" ")
          lastDecodedSpace = true

  lazy val ReverseMorse = MORSE.map((k, v) => (v, k))

  def decodeCurrentSymbol(): Unit =
    ReverseMorse.get(currentSymbol) match
      case Some(char) =>
        appendOutput(char.toString)
      case None =>
        // Handle invalid symbol
        console.warn(s"Invalid symbol sequence: $currentSymbol")
    // Optionally print nothing or a placeholder like '*' to indicate error
    // appendOutput("?")

    currentSymbol = ""
    updateDebug("")

  def appendOutput(str: String): Unit =
    val display = document.getElementById("decoded-text")
    if display != null then
      display.textContent += str
  // Auto-scroll
  // display.scrollTop = display.scrollHeight

  def updateDebug(str: String): Unit =
    val stateEl = document.getElementById("state")
    if stateEl != null then stateEl.innerText = s"Signal: ${if isSignalOn then "ON" else "OFF"} | Current: $str"