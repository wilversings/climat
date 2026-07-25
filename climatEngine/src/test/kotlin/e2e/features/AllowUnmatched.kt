package e2e.features

import e2e.E2ETestBase
import kotlin.test.Test

class AllowUnmatched : E2ETestBase() {

    @Test
    fun interpolation() {
        """
            my-root(rootArg: arg) {
                @allow-unmatched
                sub child(arg1: arg, arg2: arg) {
                    action <% echo @{__UNMATCHED} %>
                }
                
                sub child2(arg1: arg, arg2: arg) {
                    action <% echo %>
                }
            }
        """.assertResults(
            "r child a b c d e" to "echo c d e"
        ).assertThrows<Exception>(
            "r child2 a b c d e" to { }
        )
    }
}
