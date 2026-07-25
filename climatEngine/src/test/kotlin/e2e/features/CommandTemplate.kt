package e2e.features

import e2e.E2ETestBase
import kotlin.test.Test

class CommandTemplate : E2ETestBase() {

    @Test
    fun mappingFlags() {
        """
            hello-world(goodDay: flag) {
                sub foo {
                    action { echo Hello World @{goodDay} }
                }
                sub bar {
                    action { echo Hello World @{goodDay ? "today-is-a-good-day"} }
                }
                sub baz {
                    action { echo Hello World @{!goodDay ? "today-is-NOT-a-good-day"} }
                }
            }
        """
            .assertResults(
                "foo" to "echo Hello World false",

                "--goodDay foo" to "echo Hello World true",

                "bar" to "echo Hello World",

                "--goodDay bar" to "echo Hello World today-is-a-good-day",

                "baz" to "echo Hello World today-is-NOT-a-good-day",

                "--goodDay baz" to "echo Hello World"
            )
    }

    @Test
    fun mappingArgs() {
        """
            hello-world(dayOfTheWeek: arg) {
                sub foo {
                    action { echo Hello World @{dayOfTheWeek} }
                }
                sub bar {
                    action { echo Hello World @{dayOfTheWeek ? "--today-is={}"} }
                }
            }
        """
            .assertResults(
                "Tuesday foo" to "echo Hello World 'Tuesday'",

                "Tuesday bar" to "echo Hello World --today-is='Tuesday'",
            )
    }

    @Test
    fun mappingJoinersAndPlaceholderPositions() {
        """
            hello-world(day: arg?, name: arg?) {
                sub spaced {
                    action { echo @{day ? "--today-is {}"} }
                }
                sub short {
                    action { echo @{day ? "-t{}"} }
                }
                sub repeated {
                    action { echo @{name ? "--user={} --owner={}"} }
                }
                sub noPlaceholder {
                    action { echo @{day ? "--has-a-day"} }
                }
            }
        """
            .assertResults(
                "--day Tuesday spaced" to "echo --today-is 'Tuesday'",
                "spaced" to "echo",

                "--day Tuesday short" to "echo -t'Tuesday'",
                "short" to "echo",

                "--name Ada repeated" to "echo --user='Ada' --owner='Ada'",
                "repeated" to "echo",

                "--day Tuesday noPlaceholder" to "echo --has-a-day",
                "noPlaceholder" to "echo"
            )
    }

    @Test
    fun mappingEscapes() {
        """
            hello-world(name: arg) {
                action { echo @{name ? "--say=\"{}\" --literal=\{}"} }
            }
        """
            .assertResults(
                "Ada" to """echo --say="'Ada'" --literal={}"""
            )
    }
}
