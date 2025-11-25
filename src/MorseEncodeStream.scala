//> using dep dev.zio::zio:2.1.22
//> using dep dev.zio::zio-streams:2.1.22

import zio.*
import zio.Console.print
import zio.stream.{ZPipeline, ZStream}

object Morse:
  def encoder = ZPipeline.map(MORSE.get)

object MorseEncodeStream extends ZIOAppDefault:
  private def program = for
    input <- getArgs.flatMap(i => ZIO.fromOption(i.headOption))

    _ <-
      ZStream
        .fromIterable(input)
        .via(Morse.encoder)
        .tap {
          case Some(c) => print(c)
          case None    => print(" ")
        }
        .runDrain
  yield ()

  def run = program
