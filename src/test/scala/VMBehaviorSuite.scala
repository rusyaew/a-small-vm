import munit.FunSuite

final case class OutputScenario(
    label: String,
    programText: String,
    x: Long,
    y: Long,
    z: Long,
    expectedOutput: String
)

class VmBehaviorSuite extends FunSuite:

  private def parseOrFail(text: String): Vector[ThreeBitWord] =
    BytecodeParser.parseWords(text) match
      case Left(parseError) => fail(s"Unexpected parse error: ${parseError.message}")
      case Right(program)   => program

  private def runChecked(
      text: String,
      x: Long = 0L,
      y: Long = 0L,
      z: Long = 0L
  ): VmResult[VMState] =
    VirtualMachine
      .load(parseOrFail(text))
      .run(VMState.initial(x, y, z))

  private def renderedOutput(result: VmResult[VMState]): VmResult[String] =
    result.map(_.output.mkString(","))

  private def finalRegisters(result: VmResult[VMState]): VmResult[(Long, Long, Long)] =
    result.map(state => (state.x, state.y, state.z))

  private def prettyFailuresEnabled: Boolean =
    sys.props.get("vm.pretty").contains("true")

  private def assertWithVmContext[A](
      label: String,
      programText: String,
      obtained: A,
      expected: A,
      x: Long = 0L,
      y: Long = 0L,
      z: Long = 0L,
      note: Option[String] = None
  ): Unit =
    if obtained == expected then ()
    else if prettyFailuresEnabled then
      fail(
        VmPresentation.renderFailureCard(
          label = label,
          programText = programText,
          expected = expected,
          obtained = obtained,
          x = x,
          y = y,
          z = z,
          note = note
        )
      )
    else assertEquals(obtained, expected)

  private val outputScenarios = Vector(
    OutputScenario(
      label = "literal outputs can be mixed with register-based outputs",
      programText = "5,0,5,1,5,4",
      x = 10,
      y = 0,
      z = 0,
      expectedOutput = "0,1,2"
    ),
    OutputScenario(
      label = "division/output loop from the brief drains X to zero",
      programText = "0,1,5,4,3,0",
      x = 2024,
      y = 0,
      z = 0,
      expectedOutput = "4,2,5,6,7,7,7,7,3,1,0"
    ),
    OutputScenario(
      label = "provided starting state 1",
      programText = "0,1,5,4,3,0",
      x = 3729,
      y = 0,
      z = 0,
      expectedOutput = "0,4,2,1,4,2,5,6,7,3,1,0"
    ),
    OutputScenario(
      label = "provided starting state 2",
      programText = "0,3,5,4,3,0",
      x = 8642024,
      y = 0,
      z = 0,
      expectedOutput = "5,7,6,5,7,0,4,0"
    )
  )

  outputScenarios.foreach { scenario =>
    test(s"output trace: ${scenario.label}") {
      val obtained =
        renderedOutput(
          runChecked(
            text = scenario.programText,
            x = scenario.x,
            y = scenario.y,
            z = scenario.z
          )
        )

      assertWithVmContext(
        label = scenario.label,
        programText = scenario.programText,
        obtained = obtained,
        expected = Right(scenario.expectedOutput),
        x = scenario.x,
        y = scenario.y,
        z = scenario.z
      )
    }
  }

  test("yst can read register-backed combo operands") {
    val obtained = runChecked("2,6", z = 9).map(_.y)

    assertWithVmContext(
      label = "yst can read register-backed combo operands",
      programText = "2,6",
      obtained = obtained,
      expected = Right(1L),
      z = 9,
      note = Some("Combo operand 6 means register Z, and 9 mod 8 = 1.")
    )
  }

  test("yxl xors Y with a literal operand") {
    val obtained = runChecked("1,7", y = 29).map(_.y)

    assertWithVmContext(
      label = "yxl xors Y with a literal operand",
      programText = "1,7",
      obtained = obtained,
      expected = Right(26L),
      y = 29
    )
  }

  test("yxz ignores its operand and xors Y with Z") {
    val obtained = runChecked("4,7", y = 2024, z = 43690).map(_.y)

    assertWithVmContext(
      label = "yxz ignores its operand and xors Y with Z",
      programText = "4,7",
      obtained = obtained,
      expected = Right(44354L),
      y = 2024,
      z = 43690,
      note = Some("The operand is syntactically present but semantically ignored.")
    )
  }

  test("ydv uses X as the numerator, not the old value of Y") {
    val obtained = runChecked("6,1", x = 20, y = 999).map(_.y)

    assertWithVmContext(
      label = "ydv uses X as the numerator, not the old value of Y",
      programText = "6,1",
      obtained = obtained,
      expected = Right(10L),
      x = 20,
      y = 999
    )
  }

  test("jnz jumps to the literal operand when X is non-zero") {
    val obtained = renderedOutput(runChecked("3,4,5,0,5,1", x = 1))

    assertWithVmContext(
      label = "jnz jumps to the literal operand when X is non-zero",
      programText = "3,4,5,0,5,1",
      obtained = obtained,
      expected = Right("1"),
      x = 1,
      note = Some("Execution should jump to instruction pointer 4, skipping the first Out.")
    )
  }

  test("combo operand 7 is rejected for combo-based instructions") {
    val obtained = runChecked("5,7")

    assertWithVmContext(
      label = "combo operand 7 is rejected for combo-based instructions",
      programText = "5,7",
      obtained = obtained,
      expected = Left(VmError.Fetch(FetchError.InvalidComboOperand(0, 7)))
    )
  }

  test("literal operand 7 remains valid where literals are expected") {
    val obtained = runChecked("1,7").map(_.y)

    assertWithVmContext(
      label = "literal operand 7 remains valid where literals are expected",
      programText = "1,7",
      obtained = obtained,
      expected = Right(7L)
    )
  }

  test("parsing rejects non-numeric tokens") {
    val obtained = BytecodeParser.parseWords("0,hello,5")

    assertWithVmContext(
      label = "parsing rejects non-numeric tokens",
      programText = "0,hello,5",
      obtained = obtained,
      expected = Left(ParseError.ParseTokenNotInt("hello"))
    )
  }

  test("parsing rejects numbers outside the 3-bit range") {
    val obtained = BytecodeParser.parseWords("0,8,5")

    assertWithVmContext(
      label = "parsing rejects numbers outside the 3-bit range",
      programText = "0,8,5",
      obtained = obtained,
      expected = Left(ParseError.ParseTokenNotThreeBit(8))
    )
  }

  test("a trailing orphan opcode halts instead of failing") {
    val obtained = finalRegisters(runChecked("0", x = 123))

    assertWithVmContext(
      label = "a trailing orphan opcode halts instead of failing",
      programText = "0",
      obtained = obtained,
      expected = Right((123L, 0L, 0L)),
      x = 123,
      note = Some("The final single word is treated as incomplete instruction stream and halts.")
    )
  }

  test("negative division exponents surface as execute errors") {
    val obtained = runChecked("0,5", x = 8, y = -1)

    assertWithVmContext(
      label = "negative division exponents surface as execute errors",
      programText = "0,5",
      obtained = obtained,
      expected = Left(VmError.Execute(ExecuteError.NegativeExponent(-1L))),
      x = 8,
      y = -1
    )
  }

  test("negative instruction pointers surface as fetch errors") {
    val obtained =
      VirtualMachine
        .load(Vector(ThreeBitWord.W0, ThreeBitWord.W1))
        .run(VMState(x = 0, y = 0, z = 0, ip = -1, output = Vector.empty))

    assertWithVmContext(
      label = "negative instruction pointers surface as fetch errors",
      programText = "0,1",
      obtained = obtained,
      expected = Left(VmError.Fetch(FetchError.InvalidInstructionPointer(-1))),
      note = Some("This case constructs the initial state directly with ip = -1.")
    )
  }
