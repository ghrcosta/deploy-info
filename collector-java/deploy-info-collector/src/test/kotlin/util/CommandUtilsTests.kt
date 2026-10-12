package util

import io.github.ghrcosta.util.tokenizeCommand
import kotlin.test.*

class CommandUtilsTests {

    @Test
    fun `Tokenize splits simple commands on spaces`() {
        assertEquals(
            listOf("git", "status", "--porcelain", "--untracked-files=normal"),
            tokenizeCommand("git status --porcelain --untracked-files=normal")
        )
    }

    @Test
    fun `Tokenize treats quoted sections as a single argument and removes the quotes`() {
        assertEquals(
            listOf("git", "--no-pager", "diff", "/repo root/file with space.txt"),
            tokenizeCommand("git --no-pager diff \"/repo root/file with space.txt\"")
        )
    }

    @Test
    fun `Tokenize keeps unquoted arguments intact and ignores repeated spaces`() {
        assertEquals(
            listOf("a", "b c", "d"),
            tokenizeCommand("  a  \"b c\"   d  ")
        )
    }
}
