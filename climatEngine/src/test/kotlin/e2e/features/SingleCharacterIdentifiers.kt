package e2e.features

import e2e.E2ETestBase
import kotlin.test.Test

class SingleCharacterIdentifiers : E2ETestBase() {

    @Test
    fun test() {
        """
            a {
                act sh { echo a called! }
                
                @alias(r)
                @aliases(b g)
                sub c(abc a: flag) {
                    act sh { echo c called! }
                }
            }
        """.assertResults(
            "" to "echo a called!",
            "c" to "echo c called!",
            "r" to "echo c called!",
            "b" to "echo c called!",
            "g" to "echo c called!"
        )
    }
}
