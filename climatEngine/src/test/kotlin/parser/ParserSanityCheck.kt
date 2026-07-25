package parser

import com.climat.library.dslParser.dsl.decodeCliDsl
import e2e.E2ETestBase
import kotlin.test.Test

class ParserSanityCheck : E2ETestBase() {

    val cliDsl = """
        @alias(root2)
        root {
            const rootConst = "rootConst"
            
            @aliases(alias1 alias3)
            sub fe() {                           // Single line Comment
            }
            
            @seal sub jse {}
            
            /* Multi
            Line
            Comment 
            */
            
            ""${'"'}
            abc
            @param p1 doc
            ""${'"'}
            sub c3(p1 s: arg, p2 1: flag, p3: arg?, p4: flag,
               p5: arg? = "wat",
               p6: flag) {
               const myConst = "abc @{p1} cde"
               act sh { random action }
            }
            
            sub c4 {
                act scope-params
                
                @seal sub aa{
                    const ae = "wat"
                    const be = false
                }
                @shift sub bb{
                }
                @seal @shift sub cc{
                }
                @shift @seal sub dd{}
            }
        }
    """

    @Test
    fun test() {
        decodeCliDsl(cliDsl)
    }


    @Test
    fun testActionTemplateEscape() {
        """
            root {
                const cst = "my statement\""
                act sh { echo 'ab\}' @{cst} }
            }
        """.assertResults(
            "" to "echo 'ab}' my statement\""
        )
    }

    @Test
    fun testMultiBraceActionDelimiters() {
        // The body ends at the first run of `}` as long as the run of `{` that opened it, so a
        // wider delimiter lets braces through unescaped
        """
            root {
                act sh {{ if true; then echo ${'$'}{HOME}; fi }}
            }
        """.assertResults(
            "" to "if true; then echo ${'$'}{HOME}; fi"
        )

        """
            root {
                act sh {{{ echo }} }}}
            }
        """.assertResults(
            "" to "echo }}"
        )
    }
}
