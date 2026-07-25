package e2e.features

import e2e.E2ETestBase
import kotlin.test.Test

class Constants : E2ETestBase() {

    @Test
    fun interpolation() {
        """
            my-toolchain {
                const my-const = "My Dear Constant Value"
                action { echo '@{my-const}' }
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
                action { echo 'Root @{my-const}' }
                sub my-child {
                  action { echo 'Child @{my-const}' }
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
                  action { echo 'Child @{my-const}' }
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
                action { @{c};echo "from Cluj-Napoca" }
           }
        """.assertResults(
            "" to "echo \"HelloWorld!\";echo \"from Cluj-Napoca\""
        )
    }
}
