/** Layer 2: presentation-only rendering for the runnable demo.
 *
 * This is not part of the VM semantics
 *
 * Its only role is to render already-computed results in a readable
 * boxed format for the command-line demo and tests with `sbt test -Dvm.pretty=true`
 * It's basically written to show good-looking clear traces on failure like in
 * certain gdb extensions (I was mostly inspired by them when I made this)
 */

object VmPresentation:

  def renderRegisters(state: VMState): String =
    s"X=${state.x} Y=${state.y} Z=${state.z}"

  def renderTape(program: Vector[ThreeBitWord]): String =
    program.map(word => s"|${word.toInt}|").mkString

  private def renderComboOperand(operand: AnyComboOperand): String =
    operand.toInt match
      case 0 => "0"
      case 1 => "1"
      case 2 => "2"
      case 3 => "3"
      case 4 => "X"
      case 5 => "Y"
      case 6 => "Z"

  private def renderLiteralOperand(operand: AnyLiteralOperand): String =
    operand.toInt.toString

  def renderInstruction(instruction: Instruction): String =
    instruction match
      case Instruction.Xdv(operand) => s"Xdv(${renderComboOperand(operand)})"
      case Instruction.Yxl(operand) => s"Yxl(${renderLiteralOperand(operand)})"
      case Instruction.Yst(operand) => s"Yst(${renderComboOperand(operand)})"
      case Instruction.Jnz(operand) => s"Jnz(${renderLiteralOperand(operand)})"
      case Instruction.Yxz(_)       => "Yxz(_)"
      case Instruction.Out(operand) => s"Out(${renderComboOperand(operand)})"
      case Instruction.Ydv(operand) => s"Ydv(${renderComboOperand(operand)})"
      case Instruction.Zdv(operand) => s"Zdv(${renderComboOperand(operand)})"

  def instructionPreview(
                          program: Vector[ThreeBitWord],
                          maxInstructions: Int = 3
                        ): String =
    val preview =
      program
        .grouped(2)
        .take(maxInstructions)
        .toVector
        .map {
          case Vector(opcode, operand) =>
            Instruction.decode(opcode, operand) match
              case Right(instruction) => renderInstruction(instruction)
              case Left(_)            => s"<?>(${opcode.toInt},${operand.toInt})"

          case Vector(single) =>
            s"Incomplete(${single.toInt})"

          case _ =>
            "<?>"
        }

    val suffix =
      if program.length > maxInstructions * 2 then " ..."
      else ""

    preview.mkString(" -> ") + suffix

  def boxed(title: String, bodyLines: Vector[String]): String =
    val width = (title +: bodyLines).map(_.length).max

    def row(text: String): String =
      s"│ ${text.padTo(width, ' ')} │"

    val horizontal = "─" * (width + 2)

    val top = s"╭$horizontal╮"
    val divider = s"├$horizontal┤"
    val bottom = s"╰$horizontal╯"

    (Vector(top, row(title), divider) ++ bodyLines.map(row) :+ bottom).mkString("\n")

  private def renderProgramSummary(
                                    programText: String,
                                    x: Long,
                                    y: Long,
                                    z: Long
                                  ): Vector[String] =
    BytecodeParser.parseWords(programText) match
      case Left(parseError) =>
        Vector(
          s"initial: X=$x Y=$y Z=$z",
          s"program: $programText",
          s"parse  : ${parseError.message}"
        )

      case Right(program) =>
        Vector(
          s"initial: X=$x Y=$y Z=$z",
          s"tape   : ${renderTape(program)}",
          s"decode : ${instructionPreview(program)}"
        )

  def renderFailureCard(
                         label: String,
                         programText: String,
                         expected: Any,
                         obtained: Any,
                         x: Long = 0L,
                         y: Long = 0L,
                         z: Long = 0L,
                         note: Option[String] = None
                       ): String =
    val noteLines =
      note match
        case Some(text) => Vector(s"note   : $text")
        case None       => Vector.empty

    boxed(
      title = s"Failure: $label",
      bodyLines =
        renderProgramSummary(programText, x, y, z) ++
          Vector(
            s"expect : $expected",
            s"got    : $obtained"
          ) ++
          noteLines
    )

  def renderDemoCase(example: DemoCase): String =
    BytecodeParser.parseWords(example.programText) match
      case Left(parseError) =>
        boxed(
          title = example.title,
          bodyLines = Vector(
            s"initial: ${renderRegisters(example.initialState)}",
            s"program: ${example.programText}",
            s"status : PARSE ERROR",
            s"detail : ${parseError.message}"
          )
        )

      case Right(program) =>
        VirtualMachine.load(program).run(example.initialState) match
          case Left(vmError) =>
            boxed(
              title = example.title,
              bodyLines = Vector(
                s"initial: ${renderRegisters(example.initialState)}",
                s"tape   : ${renderTape(program)}",
                s"decode : ${instructionPreview(program)}",
                s"status : VM ERROR",
                s"detail : ${vmError.message}"
              )
            )

          case Right(finalState) =>
            val actualOutput = finalState.stringRepr
            val status =
              if actualOutput == example.expectedOutput then "OK"
              else "MISMATCH"

            boxed(
              title = example.title,
              bodyLines = Vector(
                s"initial: ${renderRegisters(example.initialState)}",
                s"tape   : ${renderTape(program)}",
                s"decode : ${instructionPreview(program)}",
                s"output : $actualOutput",
                s"expect : ${example.expectedOutput}",
                s"status : $status"
              )
            )