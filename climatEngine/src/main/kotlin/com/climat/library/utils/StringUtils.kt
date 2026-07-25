package com.climat.library.utils

internal fun Int.newLines() = (1..this).joinToString(emptyString()) { newLine() }
internal fun emptyString(): String = ""
internal fun newLine(): String = unixNewLine()
internal fun String?.tpl(template: (String) -> String): String =
    if (this != null) {
        template(this)
    } else {
        emptyString()
    }

internal fun windowsNewLine(): String = "\r\n"
internal fun unixNewLine(): String = "\n"
internal fun macNewLine(): String = "\r"
internal fun String.crossPlatformLineSplit(): List<String> =
    this.split(windowsNewLine(), macNewLine(), unixNewLine())

internal fun <T> Array<T>.joinToStringIfNotEmpty(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    transform: ((T) -> CharSequence)? = null
): String =
    if (this.any()) {
        this.joinToString(separator, prefix, postfix, limit, truncated, transform)
    } else emptyString()

internal fun <T> Iterable<T>.joinToStringIfNotEmpty(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    transform: ((T) -> CharSequence)? = null
): String =
    if (this.any()) {
        this.joinToString(separator, prefix, postfix, limit, truncated, transform)
    } else emptyString()

internal fun String.unescape(char: Char): String = replace("\\$char", char.toString())
internal fun String.unescape(string: String): String = replace("\\$string", string)

// Wraps a value in POSIX shell single quotes so it is passed to the shell as a single,
// literal argument (no word-splitting, globbing, or command substitution on the value).
// Embedded single quotes are handled with the standard `'\''` sequence.
internal fun shellSingleQuote(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"
