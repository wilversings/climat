package e2e.features

import e2e.E2ETestBase
import kotlin.test.Test

class Parameters : E2ETestBase() {

    @Test
    fun scope() {
        """
            hello-world(location l: arg) {
                act sh { echo Hello World from @{location} }

                sub child {
                    act sh { echo Hello Child from @{location} }
                }
            }
        """
            .assertResults(
                "Cluj-Napoca" to
                    "echo Hello World from 'Cluj-Napoca'",

                "Cluj-Napoca child" to
                    "echo Hello Child from 'Cluj-Napoca'"
            )
    }

    @Test
    fun optionals() {
        """
            hello-world(location l: arg?) {
                act sh { echo Hello World from @{location} }
            }
        """
            .assertResults(
                "" to "echo Hello World from",
                "--location Cluj-Napoca" to "echo Hello World from 'Cluj-Napoca'",
                "-l Cluj-Napoca" to "echo Hello World from 'Cluj-Napoca'"
            )
    }

    @Test
    fun defaults() {
        """
            hello-world(location l: arg? = "the other side") {
                act sh { echo Hello World from @{location} }
            }
        """
            .assertResults(
                "" to "echo Hello World from 'the other side'",
                "--location Cluj-Napoca" to "echo Hello World from 'Cluj-Napoca'"
            )
    }
}
