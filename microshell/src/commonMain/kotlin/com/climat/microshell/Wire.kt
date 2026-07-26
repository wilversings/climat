package com.climat.microshell

private const val TAG_LITERAL = "t"
private const val TAG_VALUE = "v"

/**
 * How an action body reaches the `climat-msh` process: a tagged pair per segment, each half its own
 * `argv` entry.
 *
 * There is no escaping and no quoting because `argv` entries are already delimited by the kernel.
 * That is deliberate — an encoding layer is precisely where injection bugs come back, and this
 * design exists to make them unrepresentable.
 *
 *     climat-msh  t "echo "  v "a; rm -rf /"  t " | wc -l"
 */
fun List<Segment>.encodeToArgv(): List<String> =
    flatMap { segment ->
        when (segment) {
            is Literal -> listOf(TAG_LITERAL, segment.text)
            is Value -> listOf(TAG_VALUE, segment.text)
        }
    }

internal fun decodeSegments(argv: List<String>): List<Segment> {
    if (argv.size % 2 != 0) {
        throw MicroshellParseException("Malformed action body: expected tag/text pairs")
    }
    // A plain indexed loop rather than `chunked(2)`: this is the only consumer, and Kotlin/Native's
    // dead-code elimination is coarse enough that pulling in extra collection machinery shows up in
    // the shipped binary size.
    val segments = ArrayList<Segment>(argv.size / 2)
    var i = 0
    while (i < argv.size) {
        val text = argv[i + 1]
        segments.add(
            when (val tag = argv[i]) {
                TAG_LITERAL -> Literal(text)
                TAG_VALUE -> Value(text)
                else -> throw MicroshellParseException("Malformed action body: unknown segment tag `$tag`")
            },
        )
        i += 2
    }
    return segments
}
