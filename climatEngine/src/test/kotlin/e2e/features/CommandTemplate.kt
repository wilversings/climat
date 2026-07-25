package e2e.features

import e2e.E2ETestBase
import kotlin.test.Test

class CommandTemplate : E2ETestBase() {

    @Test
    fun mappingFlags() {
        """
            hello-world(goodDay: flag) {
                sub foo {
                    act sh { echo Hello World @{goodDay} }
                }
                sub bar {
                    act sh { echo Hello World @{goodDay ? "today-is-a-good-day"} }
                }
                sub baz {
                    act sh { echo Hello World @{!goodDay ? "today-is-NOT-a-good-day"} }
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
                    act sh { echo Hello World @{dayOfTheWeek} }
                }
                sub bar {
                    act sh { echo Hello World @{dayOfTheWeek ? "--today-is={}"} }
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
                    act sh { echo @{day ? "--today-is {}"} }
                }
                sub short {
                    act sh { echo @{day ? "-t{}"} }
                }
                sub repeated {
                    act sh { echo @{name ? "--user={} --owner={}"} }
                }
                sub noPlaceholder {
                    act sh { echo @{day ? "--has-a-day"} }
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
                act sh { echo @{name ? "--say=\"{}\" --literal=\{}"} }
            }
        """
            .assertResults(
                "Ada" to """echo --say="'Ada'" --literal={}"""
            )
    }
}
