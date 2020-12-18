package cli

import org.beryx.textio.TextIoFactory


fun main() {

    val textIO = TextIoFactory.getTextIO()

    val terminal = textIO.textTerminal
    terminal.println("Blah!")

}
