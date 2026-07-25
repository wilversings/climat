package e2e.features

import e2e.E2ETestBase
import kotlin.test.Test

class Constants : E2ETestBase() {

    @Test
    fun interpolation() {
        """
            my-toolchain {
                const my-const = "My Dear Constant Value"
                act sh { echo '@{my-const}' }
            }
        """
            .assertResults(
                "" to "echo 'My Dear Constant Value'"
            )
    }

    @Test
    fun scope() {
        """
            my-toolchain {
                const my-const = "My Dear Constant Value"
                act sh { echo 'Root @{my-const}' }
                sub my-child {
                  act sh { echo 'Child @{my-const}' }
                }
            }
        """
            .assertResults(
                "" to "echo 'Root My Dear Constant Value'",
                "my-child" to "echo 'Child My Dear Constant Value'"
            )
    }

    @Test
    fun templates() {
        """
            my-toolchain {
                const my = "My"
                const dear = "Dear"
                const constant = "Constant"
                const value = "Value"
  
                const my-const = "@{my} @{dear} @{constant} @{value}"
                
                sub my-child {
                  act sh { echo 'Child @{my-const}' }
                }
            }
        """
            .assertResults(
                "my-child" to "echo 'Child My Dear Constant Value'"
            )
    }

    @Test
    fun escapeSequences() {
        """
           my-toolchain {
                const c = "echo \"HelloWorld!\""
                act sh { @{c};echo "from Cluj-Napoca" }
           }
        """.assertResults(
            "" to "echo \"HelloWorld!\";echo \"from Cluj-Napoca\""
        )
    }
}
