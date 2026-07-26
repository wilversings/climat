package com.climat.microshell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun parseText(text: String): Node = parse(listOf(Literal(text)))

/** The argv a parsed body would execute, flattened for readable assertions. */
private fun Node.argv(): List<List<String>> = when (this) {
    is Seq -> items.flatMap { it.argv() }
    is AndOr -> left.argv() + right.argv()
    is Pipeline -> stages.flatMap { it.argv() }
    is Group -> body.argv()
    is Command -> listOf(words.flatMap { it.toArgv() })
}

private fun Node.singleArgv(): List<String> = argv().single()

class ParserTest {

    @Test
    fun parsesASingleCommand() {
        assertEquals(listOf("echo", "hello"), parseText("echo hello").singleArgv())
    }

    @Test
    fun treatsNewlinesAsWhitespace() {
        // A multi-line action body: newlines separate words, not commands.
        assertEquals(listOf("echo", "a", "b"), parseText("echo\n   a\n   b").singleArgv())
    }

    @Test
    fun singleQuotesProduceOneWord() {
        assertEquals(listOf("echo", "hello world"), parseText("echo 'hello world'").singleArgv())
        assertEquals(
            listOf("git", "commit", "-m", "wip: fix"),
            parseText("git commit -m 'wip: fix'").singleArgv(),
        )
    }

    @Test
    fun quotesConcatenateWithAdjacentText() {
        assertEquals(listOf("echo", "a b c"), parseText("echo a' 'b' 'c").singleArgv())
    }

    @Test
    fun rejectsUnterminatedQuote() {
        assertFailsWith<MicroshellParseException> { parseText("echo 'oops") }
    }

    // --- structure -------------------------------------------------------------------------

    @Test
    fun buildsPipelines() {
        val node = parseText("a | b | c")
        assertEquals(Pipeline::class, node::class)
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c")), node.argv())
    }

    @Test
    fun pipeBindsTighterThanAndOr() {
        // a | b && c  ==  (a | b) && c
        val node = parseText("a | b && c") as AndOr
        assertEquals(Pipeline::class, node.left::class)
        assertEquals(Op.And, node.op)
        assertEquals(listOf("c"), node.right.singleArgv())
    }

    @Test
    fun andOrIsLeftAssociativeAndEqualPrecedence() {
        // a && b || c  ==  (a && b) || c
        val node = parseText("a && b || c") as AndOr
        assertEquals(Op.Or, node.op)
        val left = node.left as AndOr
        assertEquals(Op.And, left.op)
    }

    @Test
    fun semicolonIsLoosest() {
        val node = parseText("a && b ; c") as Seq
        assertEquals(2, node.items.size)
        assertEquals(AndOr::class, node.items[0]::class)
    }

    @Test
    fun allowsTrailingSemicolon() {
        assertEquals(listOf("a"), parseText("a ;").singleArgv())
    }

    @Test
    fun groupingOverridesPrecedence() {
        // a || (b && c) — the group must be the right operand, not a flattened chain.
        val node = parseText("a || (b && c)") as AndOr
        assertEquals(Op.Or, node.op)
        assertEquals(Group::class, node.right::class)
    }

    @Test
    fun groupCanBeAPipelineStage() {
        val node = parseText("(a ; b) | c") as Pipeline
        assertEquals(Group::class, node.stages[0]::class)
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c")), node.argv())
    }

    @Test
    fun rejectsUnbalancedParen() {
        assertFailsWith<MicroshellParseException> { parseText("(a && b") }
        assertFailsWith<MicroshellParseException> { parseText("a )") }
    }

    @Test
    fun rejectsDanglingOperators() {
        assertFailsWith<MicroshellParseException> { parseText("a &&") }
        assertFailsWith<MicroshellParseException> { parseText("| a") }
        assertFailsWith<MicroshellParseException> { parseText("") }
    }

    // --- assignments -----------------------------------------------------------------------

    @Test
    fun parsesEnvAssignments() {
        val command = parseText("A=1 B=2 cmd") as Command
        assertEquals(listOf("A", "B"), command.assignments.map { it.name })
        assertEquals(listOf("1", "2"), command.assignments.map { it.value.toArgv().single() })
        assertEquals(listOf("cmd"), command.words.flatMap { it.toArgv() })
    }

    @Test
    fun assignmentAfterACommandIsJustAnArgument() {
        assertEquals(listOf("cmd", "A=1"), parseText("cmd A=1").singleArgv())
    }

    @Test
    fun quotedAssignmentIsAWordNotAnAssignment() {
        assertEquals(listOf("A=1", "x"), parseText("'A=1' x").singleArgv())
    }

    @Test
    fun rejectsAssignmentWithoutCommand() {
        assertFailsWith<MicroshellParseException> { parseText("A=1") }
    }

    @Test
    fun rejectsUnsupportedSyntax() {
        listOf("a > b", "a < b", "a & b", "echo \$HOME", "echo \"x\"", "echo `x`").forEach {
            assertFailsWith<MicroshellParseException>("`$it` should be rejected") { parseText(it) }
        }
    }

    // --- values are never lexed --------------------------------------------------------------

    @Test
    fun aValueIsExactlyOneArgvEntry() {
        val node = parse(listOf(Literal("echo "), Value("two words")))
        assertEquals(listOf("echo", "two words"), node.singleArgv())
    }

    @Test
    fun valuesAreNeverReLexed() {
        // The whole security model: a resolved value cannot become syntax.
        listOf(
            "a; rm -rf /",
            "a | b && c",
            "'quoted'",
            "\$(whoami)",
            "`whoami`",
            "> /etc/passwd",
            "  leading and trailing  ",
        ).forEach { hostile ->
            val node = parse(listOf(Literal("echo "), Value(hostile)))
            assertEquals(listOf("echo", hostile), node.singleArgv(), "`$hostile` leaked into syntax")
        }
    }

    @Test
    fun aValueJoinsAdjacentLiteralsIntoOneWord() {
        val node = parse(listOf(Literal("cmd --name="), Value("value"), Literal("-suffix")))
        assertEquals(listOf("cmd", "--name=value-suffix"), node.singleArgv())
    }

    @Test
    fun aLoneEmptyValueDropsItsWord() {
        // An unset flag's mapping must not leave a stray empty argument.
        val node = parse(listOf(Literal("echo a "), Value(""), Literal(" b")))
        assertEquals(listOf("echo", "a", "b"), node.singleArgv())
    }

    @Test
    fun anEmptyValueMixedWithTextKeepsTheWord() {
        val node = parse(listOf(Literal("cmd --name="), Value("")))
        assertEquals(listOf("cmd", "--name="), node.singleArgv())
    }

    @Test
    fun spaceSeparatedValuesBecomeSeparateWords() {
        // How a multi-valued ref arrives: one value each, split by a literal space.
        val node = parse(listOf(Literal("cmd "), Value("a b"), Literal(" "), Value("c")))
        assertEquals(listOf("cmd", "a b", "c"), node.singleArgv())
    }

    @Test
    fun valuesWorkInAssignments() {
        val command = parse(listOf(Literal("VAR="), Value("x y"), Literal(" cmd"))) as Command
        assertEquals("VAR", command.assignments.single().name)
        assertEquals("x y", command.assignments.single().value.toArgv().single())
        assertEquals(listOf("cmd"), command.words.flatMap { it.toArgv() })
    }

    // --- wire format -------------------------------------------------------------------------

    @Test
    fun wireFormatRoundTrips() {
        val segments = listOf(
            Literal("echo "),
            Value("a; rm -rf /"),
            Literal(" | wc -l"),
        )
        assertEquals(segments, decodeSegments(segments.encodeToArgv()))
    }

    @Test
    fun wireFormatSurvivesValuesThatLookLikeTags() {
        // argv entries are kernel-delimited, so a value of "t" or "v" cannot confuse the reader.
        val segments = listOf(Literal("echo "), Value("t"), Literal(" "), Value("v"))
        assertEquals(segments, decodeSegments(segments.encodeToArgv()))
    }

    @Test
    fun rejectsMalformedWire() {
        assertFailsWith<MicroshellParseException> { decodeSegments(listOf("t")) }
        assertFailsWith<MicroshellParseException> { decodeSegments(listOf("nope", "x")) }
    }

    @Test
    fun wireFormatIsFlat() {
        assertEquals(
            listOf("t", "echo ", "v", "hi"),
            listOf(Literal("echo "), Value("hi")).encodeToArgv(),
        )
    }
}
