enum ParseError:
  case ParseTokenNotInt(token: String)
  case ParseTokenNotThreeBit(number: Int)

  def message: String =
    this match
      case ParseTokenNotInt(token) =>
        s"Invalid token: '$token'"
      case ParseTokenNotThreeBit(number) =>
        s"Invalid 3-bit (0..7) number: $number"

enum FetchError:
  case InvalidInstructionPointer(ip: Int)
  case InvalidComboOperand(ip: Int, operand: Int)

  def message: String =
    this match
      case InvalidInstructionPointer(ip) =>
        s"Invalid instruction pointer: $ip"
      case InvalidComboOperand(ip, operand) =>
        s"Invalid combo operand at ip=$ip: $operand"

enum ExecuteError:
  case NegativeExponent(exponent: Long) // we could interpret X/2^(-k) as X * 2^k, but it's a stretch from specification

  def message: String =
    this match
      case NegativeExponent(exponent) =>
        s"Invalid division exponent: $exponent"

enum VmError:
  case Fetch(problem: FetchError)
  case Execute(problem: ExecuteError)

  def message: String =
    this match
      case Fetch(problem)   => problem.message
      case Execute(problem) => problem.message

type ParseResult[A] = Either[ParseError, A]
type FetchPhaseResult[A] = Either[FetchError, A]
type ExecutionResult[A] = Either[ExecuteError, A]
type VmResult[A] = Either[VmError, A]

