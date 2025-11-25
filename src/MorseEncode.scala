def morseEncode(input: String) =
  input.map(MORSE.get)

@main def morseEncodeApp(input: String) =
  morseEncode(input).foreach:
    case Some(symbol) => print(symbol)
    case None         => print(" ")

  println()
