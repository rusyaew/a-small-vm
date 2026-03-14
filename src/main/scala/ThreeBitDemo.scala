final case class DemoCase(
    title: String,
    initialState: VMState,
    programText: String,
    expectedOutput: String
)

object Main:

  private val demoCases = Vector(
    DemoCase(
      title = "Provided starting state 1",
      initialState = VMState.initial(x = 3729, y = 0, z = 0),
      programText = "0,1,5,4,3,0",
      expectedOutput = "0,4,2,1,4,2,5,6,7,3,1,0"
    ),
    DemoCase(
      title = "Provided starting state 2",
      initialState = VMState.initial(x = 8642024, y = 0, z = 0),
      programText = "0,3,5,4,3,0",
      expectedOutput = "5,7,6,5,7,0,4,0"
    )
  )

  @main def runVm(): Unit =
    println(demoCases.map(VmPresentation.renderDemoCase).mkString("\n\n"))
