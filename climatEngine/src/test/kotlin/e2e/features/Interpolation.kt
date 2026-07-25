package e2e.features

import e2e.E2ETestBase
import kotlin.test.Test

class Interpolation : E2ETestBase() {

    private val dollar = "\$"

    @Test
    fun shellCommandSubstitutionPassesThrough() {
        // `$(...)` is plain shell now; only `@{...}` interpolates
        """
            demo(name: arg) {
                action <% echo ${dollar}(date) @{name} %>
            }
        """
            .assertResults(
                "world" to "echo ${dollar}(date) world"
            )
    }

    @Test
    fun bareDollarIsPlainText() {
        """
            demo {
                const price = "5${dollar}"
                action <% echo @{price} %>
            }
        """
            .assertResults(
                "" to "echo 5${dollar}"
            )
    }

    @Test
    fun bareAtIsPlainText() {
        // A lone `@` (not `@{`) needs no escaping, e.g. scp/git/npm usages
        """
            demo {
                const email = "team@corp.com"
                action <% scp file user@host:/tmp && echo @{email} %>
            }
        """
            .assertResults(
                "" to "scp file user@host:/tmp && echo team@corp.com"
            )
    }

    @Test
    fun escapedInterpolationMarkerIsLiteral() {
        """
            demo {
                action <% echo \@{home} %>
            }
        """
            .assertResults(
                "" to "echo @{home}"
            )
    }
}
