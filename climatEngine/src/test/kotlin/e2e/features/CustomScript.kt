package e2e.features

import com.climat.library.commandParser.parse
import com.climat.library.domain.action.JavaScriptActionValue
import com.climat.library.validation.validate
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomScript {

    @Test
    fun testCompiles() {
        validate(
            parse(
                """
                hello-world(location l: arg) {
    
                    javascript action {{
                        console.log("Hello World from a JavaScript environment !");
                        console.log("It seems that you are situated in ", params.location);
                        console.log("Is that correct?");
                    }}
                    
                }
                """
            )
        )
    }

    @Test
    fun bracesInsideAWiderDelimiterNeedNoEscaping() {
        val script = customScriptOf(
            """
                hello-world {
                    javascript action {{
                        if (a > b) { console.log("a is greater than b") }
                    }}
                }
            """
        )
        assertEquals("""if (a > b) { console.log("a is greater than b") }""", script.trim())
    }

    @Test
    fun escapeSequence() {
        // A `}` run as long as the opening one has to be escaped to stay part of the script
        val script = customScriptOf(
            """
                hello-world {
                    javascript action { console.log("closing brace: \}") }
                }
            """
        )
        assertEquals("""console.log("closing brace: }")""", script.trim())
    }

    private fun customScriptOf(cliDsl: String): String {
        val toolchain = parse(cliDsl)
        validate(toolchain)
        return (toolchain.action as JavaScriptActionValue).customScript
    }
}
