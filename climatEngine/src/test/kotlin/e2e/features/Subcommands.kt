package e2e.features

import e2e.E2ETestBase
import kotlin.test.Test

class Subcommands : E2ETestBase() {

    @Test
    fun noop() {
        """
            my-toolchain {
                sub noop {}
            }
        """
            .assertResults(
                "" to null,
                "noop" to null
            )
    }

    @Test
    fun aliases() {
        """
            my-toolchain {

                @aliases(ct child) // Defines one or more aliases
                @alias(cld)          // Defines one alias
                sub child-toolchain {
                    action <% echo 'Child' %>
                }

            }
        """
            .assertResults(
                "child-toolchain" to "echo 'Child'",
                "child" to "echo 'Child'",
                "ct" to "echo 'Child'",
                "cld" to "echo 'Child'"
            )
    }

    @Test
    fun subcommandsWithParameters() {
        """
            my-toolchain {
                sub abc(p1: arg?) {
                }
            
                sub child(p1: arg?) {
                    action <% echo 'i am child with param @{p1}' %>
                }
            }
        """
            .assertResults(
                "child --p1 bcd" to "echo 'i am child with param bcd'",
                "child --p1 abc" to "echo 'i am child with param abc'"
            )
    }

}
