import scala.annotation.tailrec
import ComboOperand.*

final case class VMState(
                        x: Long,
                        y: Long,
                        z: Long,
                        ip: Int = 0,
                        output: Vector[Int] = Vector.empty
                        )

object VMState:
  def initial(x: Long, y: Long, z: Long): VMState =
    VMState(x, y, z)

  extension (state: VMState)
    def stringRepr: String =
      state.output.mkString(",")

enum StepResult:
  case Continue(nextState: VMState)
  case Halt(finalState: VMState)

enum FetchResult:
  case Decoded(instruction: Instruction)
  case Halt

object VMInstructionInterpreter:
  private def remainderMod8(value: Long): Int =
    Math.floorMod(value, 8L).toInt

  private def divideByPowerOfTwo(numerator: Long, exponent: Long): ExecutionResult[Long] =
    if exponent < 0 then
      Left(ExecuteError.NegativeExponent(exponent))
    else if exponent < 63 then
      Right(numerator / (1L << exponent.toInt))
    else if exponent == 63 then
      Right(if numerator == Long.MinValue then -1L else 0L) // since Long is asymmetric, range -2^63...2^63-1
    else
      Right(0L)

  def applyInstructionToState(instruction: Instruction, state: VMState): ExecutionResult[StepResult] =
    instruction match
      case Instruction.Xdv(operand) =>
        divideByPowerOfTwo(
          numerator = state.x,
          exponent = operand.interpretInContext(state.x, state.y, state.z)
        ).map { newX =>
          StepResult.Continue(state.copy(x = newX, ip = state.ip + 2))
        }

      case Instruction.Ydv(operand) =>
        divideByPowerOfTwo(
          numerator = state.x,
          exponent = operand.interpretInContext(state.x, state.y, state.z)
        ).map { newY =>
          StepResult.Continue(state.copy(y = newY, ip = state.ip + 2))
        }

      case Instruction.Zdv(operand) =>
        divideByPowerOfTwo(
          numerator = state.x,
          exponent = operand.interpretInContext(state.x, state.y, state.z)
        ).map { newZ =>
          StepResult.Continue(state.copy(z = newZ, ip = state.ip + 2))
        }

      case Instruction.Yxl(operand) =>
        Right(StepResult.Continue(
            state.copy(
              y = state.y ^ operand.toInt.toLong,
              ip = state.ip + 2
        )))

      case Instruction.Yst(operand) =>
        Right(StepResult.Continue(state.copy(
          y = remainderMod8(operand.interpretInContext(state.x, state.y, state.z)).toLong,
          ip = state.ip + 2
        )))

      case Instruction.Jnz(operand) =>
        Right(StepResult.Continue(state.copy(
          ip = if state.x == 0 then state.ip + 2 else operand.toInt
        )))

      case Instruction.Yxz(_) =>
        Right(StepResult.Continue(state.copy(
          y = state.y ^ state.z,
          ip = state.ip + 2
        )))

      case Instruction.Out(operand) =>
        Right(StepResult.Continue(state.copy(
          ip = state.ip + 2,
          output = state.output :+ remainderMod8(
            operand.interpretInContext(state.x, state.y, state.z)
          )
        )))

final case class VirtualMachine(program: Vector[ThreeBitWord]):
  private def fetchInstruction(state: VMState): FetchPhaseResult[FetchResult] =
    if state.ip < 0 then
      Left(FetchError.InvalidInstructionPointer(state.ip))
    else if state.ip + 1 >= program.length then
      Right(FetchResult.Halt)
    else
      val opcodeWord = program(state.ip)
      val operandWord = program(state.ip + 1)

      Instruction
        .decode(opcodeWord, operandWord)
        .left
        .map({
          case DecodeError.InvalidComboOperand(operand) =>
            FetchError.InvalidComboOperand(state.ip, operand)
        })
        .map(FetchResult.Decoded.apply)

  def step(state: VMState): VmResult[StepResult] =
    fetchInstruction(state)
      .left
      .map(VmError.Fetch.apply)
      .flatMap {
        case FetchResult.Halt =>
          Right(StepResult.Halt(state))

        case FetchResult.Decoded(instruction) =>
          VMInstructionInterpreter
            .applyInstructionToState(instruction, state)
            .left
            .map(VmError.Execute.apply)
      }

  @tailrec
  final def run(state: VMState): VmResult[VMState] =
    step(state) match
      case Left(error)                            => Left(error)
      case Right(StepResult.Halt(finalState))     => Right(finalState)
      case Right(StepResult.Continue(nextState))  => run(nextState)

object VirtualMachine:
  def load(program: Vector[ThreeBitWord]): VirtualMachine =
    VirtualMachine(program)
