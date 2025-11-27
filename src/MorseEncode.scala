//> using platform jvm

def morseEncode(input: String) = 
  input.map(c => MORSE.get(c))

@main def morseEncodeApp(input: String) =
  morseEncode(input).foreach:
    case Some(symbol) => print(symbol)
    case None         => print(" ")

  println()
