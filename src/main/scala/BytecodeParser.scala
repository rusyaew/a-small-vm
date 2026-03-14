import ParseError.ParseTokenNotInt

object BytecodeParser:

  private def splitProgram(text: String): List[String] =
    text.split(",").toList.map(_.trim).filter(_.nonEmpty)

  private def decodeToken(token: String): ParseResult[ThreeBitWord] =
    token.toIntOption
      .toRight(ParseTokenNotInt(token))
      .flatMap(ThreeBitWord.fromInt)

  def parseWords(text: String): ParseResult[Vector[ThreeBitWord]] =
    splitProgram(text).foldRight[ParseResult[Vector[ThreeBitWord]]](Right(Vector.empty)) {
      case (token, acc) =>
        decodeToken(token).flatMap(word => acc.map(word +: _))
    }
