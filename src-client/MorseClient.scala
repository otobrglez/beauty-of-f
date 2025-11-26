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

  // Configuration matching server's Config
  val Wpm      = 12.0      // MUST match server's wpm
  val UnitTime = 1.2 / Wpm // ~0.1s per unit at 12 WPM

  // Thresholds based on server's unit timing
  // Short noise filter: < 0.4 unit (filter out very short noise)
  val NoiseThreshold = UnitTime * 0.4

  // Dot vs Dash:
  // Dot should be ~1 unit. Dash ~3 units.
  // Threshold at 2.0 units.
  val DotDashThreshold = UnitTime * 2.0

  // Letter Gap:
  // Standard is 3 units. We use 2.5 to be more forgiving
  val LetterGapThreshold = UnitTime * 2.5

  // Word Gap:
  // Standard is 7 units. We use 6 to be more forgiving
  val WordGapThreshold = UnitTime * 6.0

  val TargetFreq = 600
  val Threshold  = 0.02 // Lower threshold for better sensitivity

  // State
  var isListening                         = false
  var audioContext: dom.AudioContext      = null
  var analyser: dom.AnalyserNode          = null
  var dataArray: js.typedarray.Uint8Array = null
  var isSignalOn                          = false
  var lastChangeTime                      = 0.0
  var currentSymbol                       = ""
  var lastDecodedSpace                    = false
  var silenceStartTime                    = 0.0 // Track silence start separately

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
        filter.Q.value = 15 // Narrower band for better selectivity

        analyser = audioContext.createAnalyser()
        analyser.fftSize = 512
        analyser.smoothingTimeConstant = 0.85 // More smoothing for stable detection

        source.connect(filter)
        filter.connect(analyser)

        dataArray = new js.typedarray.Uint8Array(analyser.frequencyBinCount)

        lastChangeTime = Date.now()
        silenceStartTime = Date.now() // Initialize silence tracking

        val state = document.getElementById("state")
        if state != null then state.textContent = s"Listening... (600Hz at ${Wpm.toInt} WPM)"

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
        val toneDuration = (now - lastChangeTime) / 1000.0
        if toneDuration < NoiseThreshold then
          // Too short, likely noise. Ignore this signal entirely.
          console.log(f"Noise ignored: ${toneDuration}%.3fs")
        else if toneDuration < DotDashThreshold then
          currentSymbol += "."
          updateDebug(currentSymbol)
        else
          currentSymbol += "-"
          updateDebug(currentSymbol)

        silenceStartTime = now // Start tracking silence
      else
        // OFF -> ON (Silence ended)
        val silenceDuration = (now - silenceStartTime) / 1000.0

        // Check if we should decode the previous symbol
        if currentSymbol.nonEmpty && silenceDuration > LetterGapThreshold then decodeCurrentSymbol()

        // Check for word gap
        if silenceDuration > WordGapThreshold then
          appendOutput(" ")
          lastDecodedSpace = true
        else lastDecodedSpace = false

      isSignalOn = signalNow
      lastChangeTime = now
    else
    // No state change
    if !isSignalOn then
      // Silence duration check
      val silenceDuration = (now - silenceStartTime) / 1000.0

      if currentSymbol.nonEmpty && silenceDuration > LetterGapThreshold then
        decodeCurrentSymbol()
        lastDecodedSpace = true // Prevent immediate word gap

      if !lastDecodedSpace && silenceDuration > WordGapThreshold then
        appendOutput(" ")
        lastDecodedSpace = true

  lazy val ReverseMorse = MORSE.map((k, v) => (v, k))

  def decodeCurrentSymbol(): Unit =
    ReverseMorse.get(currentSymbol) match
      case Some(char) =>
        appendOutput(char.toString)
        console.log(s"Decoded: '$currentSymbol' -> '$char'")
      case None       =>
        // Handle invalid symbol - show it in brackets
        appendOutput(s"[$currentSymbol]")
        console.warn(s"Invalid symbol sequence: $currentSymbol")

    currentSymbol = ""
    updateDebug("")

  def appendOutput(str: String): Unit =
    val display = document.getElementById("decoded-text")
    if display != null then
      display.textContent += str
      // Auto-scroll if element supports it
      display.asInstanceOf[js.Dynamic].scrollTop = display.asInstanceOf[js.Dynamic].scrollHeight

  def updateDebug(str: String): Unit =
    val stateEl = document.getElementById("state")
    if stateEl != null then
      val wpmInfo = s"${Wpm.toInt} WPM"
      stateEl.innerText = s"Signal: ${if isSignalOn then "ON" else "OFF"} | Current: $str | $wpmInfo"
