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
                "world" to "echo ${dollar}(date) 'world'"
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

    @Test
    fun userArgsAreIsolatedFromTheShell() {
        // A user-supplied arg is single-quoted, so shell metacharacters in its value are inert
        // (no word-splitting, command substitution, or injection).
        """
            demo(x: arg) {
                action <% echo @{x} %>
            }
        """
            .assertResults(
                "a;b${dollar}(evil)" to "echo 'a;b${dollar}(evil)'",
                // an embedded single quote is escaped with the POSIX '\'' sequence
                "it's" to "echo 'it'\\''s'"
            )
    }
}
