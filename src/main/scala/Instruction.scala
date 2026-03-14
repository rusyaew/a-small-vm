sealed trait OperandKind
sealed trait LiteralKind extends OperandKind
sealed trait ComboKind extends OperandKind

enum DecodeError:
  case InvalidComboOperand(operand: Int)

type DecodeResult[A] = Either[DecodeError, A]

enum ThreeBitWord(val toInt: Int):
  case W0 extends ThreeBitWord(0)
  case W1 extends ThreeBitWord(1)
  case W2 extends ThreeBitWord(2)
  case W3 extends ThreeBitWord(3)
  case W4 extends ThreeBitWord(4)
  case W5 extends ThreeBitWord(5)
  case W6 extends ThreeBitWord(6)
  case W7 extends ThreeBitWord(7)

object ThreeBitWord:
  def fromInt(n: Int): ParseResult[ThreeBitWord] =
    n match
      case 0 => Right(ThreeBitWord.W0)
      case 1 => Right(ThreeBitWord.W1)
      case 2 => Right(ThreeBitWord.W2)
      case 3 => Right(ThreeBitWord.W3)
      case 4 => Right(ThreeBitWord.W4)
      case 5 => Right(ThreeBitWord.W5)
      case 6 => Right(ThreeBitWord.W6)
      case 7 => Right(ThreeBitWord.W7)
      case _ => Left(ParseError.ParseTokenNotThreeBit(n))

type ValidComboWord =
  ThreeBitWord.W0.type |
  ThreeBitWord.W1.type |
  ThreeBitWord.W2.type |
  ThreeBitWord.W3.type |
  ThreeBitWord.W4.type |
  ThreeBitWord.W5.type |
  ThreeBitWord.W6.type

opaque type Operand[K <: OperandKind, W <: ThreeBitWord] <: W = W

object Operand:
  def literal[W <: ThreeBitWord](word: W): Operand[LiteralKind, W] =
    word

  def combo[W <: ValidComboWord](word: W): Operand[ComboKind, W] =
    word

  private def literalWord(operand: Operand[LiteralKind, ThreeBitWord]): ThreeBitWord =
    operand

  private def comboWord(operand: Operand[ComboKind, ValidComboWord]): ValidComboWord =
    operand

  extension (operand: Operand[ComboKind, ValidComboWord])
    def comboValue(x: Long, y: Long, z: Long): Long =
      comboWord(operand) match
        case ThreeBitWord.W0 => 0L
        case ThreeBitWord.W1 => 1L
        case ThreeBitWord.W2 => 2L
        case ThreeBitWord.W3 => 3L
        case ThreeBitWord.W4 => x
        case ThreeBitWord.W5 => y
        case ThreeBitWord.W6 => z

type LiteralOperand[W <: ThreeBitWord] = Operand[LiteralKind, W]
type ComboOperand[W <: ValidComboWord] = Operand[ComboKind, W]

type AnyLiteralOperand  = Operand[LiteralKind, ThreeBitWord]
type AnyComboOperand    = Operand[ComboKind, ValidComboWord]

object LiteralOperand:
  def fromWord(word: ThreeBitWord): AnyLiteralOperand =
    Operand.literal(word)

object ComboOperand:
  def fromWord(word: ThreeBitWord): DecodeResult[AnyComboOperand] =
    word match
      case ThreeBitWord.W0 => Right(Operand.combo(ThreeBitWord.W0))
      case ThreeBitWord.W1 => Right(Operand.combo(ThreeBitWord.W1))
      case ThreeBitWord.W2 => Right(Operand.combo(ThreeBitWord.W2))
      case ThreeBitWord.W3 => Right(Operand.combo(ThreeBitWord.W3))
      case ThreeBitWord.W4 => Right(Operand.combo(ThreeBitWord.W4))
      case ThreeBitWord.W5 => Right(Operand.combo(ThreeBitWord.W5))
      case ThreeBitWord.W6 => Right(Operand.combo(ThreeBitWord.W6))
      case ThreeBitWord.W7 => Left(DecodeError.InvalidComboOperand(7))

  extension (operand: AnyComboOperand)
    def interpretInContext(x: Long, y: Long, z: Long): Long =
      operand match
        case ThreeBitWord.W0 => 0L
        case ThreeBitWord.W1 => 1L
        case ThreeBitWord.W2 => 2L
        case ThreeBitWord.W3 => 3L
        case ThreeBitWord.W4 => x
        case ThreeBitWord.W5 => y
        case ThreeBitWord.W6 => z

enum Opcode:
  case Xdv, Yxl, Yst, Jnz, Yxz, Out, Ydv, Zdv

object Opcode:
  def fromWord(word: ThreeBitWord): Opcode =
    word match
      case ThreeBitWord.W0 => Opcode.Xdv
      case ThreeBitWord.W1 => Opcode.Yxl
      case ThreeBitWord.W2 => Opcode.Yst
      case ThreeBitWord.W3 => Opcode.Jnz
      case ThreeBitWord.W4 => Opcode.Yxz
      case ThreeBitWord.W5 => Opcode.Out
      case ThreeBitWord.W6 => Opcode.Ydv
      case ThreeBitWord.W7 => Opcode.Zdv

enum Instruction:
  case Xdv(operand: AnyComboOperand)      // X := trunc(X / 2^combo)
  case Yxl(operand: AnyLiteralOperand)    // Y := Y xor literal
  case Yst(operand: AnyComboOperand)      // Y := combo mod 8
  case Jnz(operand: AnyLiteralOperand)    // if X != 0 then ip := literal
  case Yxz(ignored: AnyLiteralOperand)    // Y := Y xor Z; operand is ignored
  case Out(operand: AnyComboOperand)      // output combo mod 8
  case Ydv(operand: AnyComboOperand)      // Y := trunc(X / 2^combo)
  case Zdv(operand: AnyComboOperand)      // Z := trunc(X / 2^combo)

object Instruction:
  private def lit(operand: ThreeBitWord)
                 (mk: AnyLiteralOperand => Instruction): DecodeResult[Instruction] =
                  Right(mk(LiteralOperand.fromWord(operand)))

  private def combo(operand: ThreeBitWord)
                   (mk: AnyComboOperand => Instruction): DecodeResult[Instruction] =
                    ComboOperand.fromWord(operand).map(mk)


  def decode(opcode: ThreeBitWord, operand: ThreeBitWord): DecodeResult[Instruction] =
    Opcode.fromWord(opcode) match
      case Opcode.Xdv => combo(operand)(Instruction.Xdv.apply)
      case Opcode.Yxl => lit(operand)(Instruction.Yxl.apply)
      case Opcode.Yst => combo(operand)(Instruction.Yst.apply)
      case Opcode.Jnz => lit(operand)(Instruction.Jnz.apply)
      case Opcode.Yxz => lit(operand)(Instruction.Yxz.apply)
      case Opcode.Out => combo(operand)(Instruction.Out.apply)
      case Opcode.Ydv => combo(operand)(Instruction.Ydv.apply)
      case Opcode.Zdv => combo(operand)(Instruction.Zdv.apply)