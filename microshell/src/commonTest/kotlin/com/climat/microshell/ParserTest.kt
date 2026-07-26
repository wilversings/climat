package com.climat.microshell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun parseText(text: String): Node = parse(listOf(Literal(text)))

/** Resolves every hole to `<id>` so structure is easy to assert on. */
private fun Node.render(values: Map<Int, String> = emptyMap()): ResolvedNode =
    resolve { hole -> listOf(values[hole.id] ?: "<${hole.id}>") }

private fun Node.renderMulti(values: Map<Int, List<String>>): ResolvedNode =
    resolve { hole -> values[hole.id] ?: listOf("<${hole.id}>") }

private fun cmd(vararg argv: String) = RCommand(emptyList(), argv.toList())

class ParserTest {

    @Test
    fun parsesASingleCommand() {
        assertEquals(cmd("echo", "hello"), parseText("echo hello").render())
    }

    @Test
    fun collapsesWhitespaceAndNewlines() {
        // A multi-line action body: newlines are word separators, not command separators.
        assertEquals(cmd("echo", "a", "b"), parseText("echo\n   a\n   b").render())
    }

    @Test
    fun singleQuotesProduceOneWord() {
        assertEquals(cmd("echo", "hello world"), parseText("echo 'hello world'").render())
        assertEquals(cmd("git", "commit", "-m", "wip: fix"), parseText("git commit -m 'wip: fix'").render())
    }

    @Test
    fun quotesConcatenateWithAdjacentText() {
        assertEquals(cmd("echo", "a b c"), parseText("echo a' 'b' 'c").render())
    }

    @Test
    fun rejectsUnterminatedQuote() {
        assertFailsWith<MicroshellParseException> { parseText("echo 'oops") }
    }

    @Test
    fun buildsPipelines() {
        assertEquals(
            RPipeline(listOf(cmd("a"), cmd("b"), cmd("c"))),
            parseText("a | b | c").render(),
        )
    }

    @Test
    fun pipeBindsTighterThanAndOr() {
        // a | b && c  ==  (a | b) && c
        assertEquals(
            RAndOr(RPipeline(listOf(cmd("a"), cmd("b"))), Op.And, cmd("c")),
            parseText("a | b && c").render(),
        )
    }

    @Test
    fun andOrIsLeftAssociativeAndEqualPrecedence() {
        // a && b || c  ==  (a && b) || c
        assertEquals(
            RAndOr(RAndOr(cmd("a"), Op.And, cmd("b")), Op.Or, cmd("c")),
            parseText("a && b || c").render(),
        )
    }

    @Test
    fun semicolonIsLoosest() {
        assertEquals(
            RSeq(listOf(RAndOr(cmd("a"), Op.And, cmd("b")), cmd("c"))),
            parseText("a && b ; c").render(),
        )
    }

    @Test
    fun allowsTrailingSemicolon() {
        assertEquals(cmd("a"), parseText("a ;").render())
    }

    @Test
    fun groupingOverridesPrecedence() {
        assertEquals(
            RAndOr(cmd("a"), Op.Or, RAndOr(cmd("b"), Op.And, cmd("c"))),
            parseText("a || (b && c)").render(),
        )
    }

    @Test
    fun groupCanBeAPipelineStage() {
        assertEquals(
            RPipeline(listOf(RSeq(listOf(cmd("a"), cmd("b"))), cmd("c"))),
            parseText("(a ; b) | c").render(),
        )
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

    @Test
    fun parsesEnvAssignments() {
        assertEquals(
            RCommand(listOf("A" to "1", "B" to "2"), listOf("cmd")),
            parseText("A=1 B=2 cmd").render(),
        )
    }

    @Test
    fun assignmentAfterACommandIsJustAnArgument() {
        assertEquals(cmd("cmd", "A=1"), parseText("cmd A=1").render())
    }

    @Test
    fun quotedAssignmentIsAWordNotAnAssignment() {
        assertEquals(cmd("A=1", "x"), parseText("'A=1' x").render())
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

    // --- holes -----------------------------------------------------------------------------

    @Test
    fun aHoleIsExactlyOneArgvEntry() {
        val node = parse(listOf(Literal("echo "), Hole(0)))
        assertEquals(cmd("echo", "two words"), node.render(mapOf(0 to "two words")))
    }

    @Test
    fun holeValuesAreNeverReLexed() {
        // The whole point: an injected value stays inert.
        val node = parse(listOf(Literal("echo "), Hole(0)))
        assertEquals(cmd("echo", "a; rm -rf /"), node.render(mapOf(0 to "a; rm -rf /")))
        assertEquals(cmd("echo", "a | b && c"), node.render(mapOf(0 to "a | b && c")))
        assertEquals(cmd("echo", "'quoted'"), node.render(mapOf(0 to "'quoted'")))
    }

    @Test
    fun holeJoinsAdjacentLiteralsIntoOneWord() {
        val node = parse(listOf(Literal("cmd --name="), Hole(0), Literal("-suffix")))
        assertEquals(cmd("cmd", "--name=value-suffix"), node.render(mapOf(0 to "value")))
    }

    @Test
    fun aLoneHoleResolvingEmptyDropsTheWord() {
        // An unset flag's mapping must not leave a stray empty argument.
        val node = parse(listOf(Literal("echo a "), Hole(0), Literal(" b")))
        assertEquals(cmd("echo", "a", "b"), node.render(mapOf(0 to "")))
    }

    @Test
    fun anEmptyHoleMixedWithTextKeepsTheWord() {
        val node = parse(listOf(Literal("cmd --name="), Hole(0)))
        assertEquals(cmd("cmd", "--name="), node.render(mapOf(0 to "")))
    }

    @Test
    fun holesWorkInAssignmentValues() {
        val node = parse(listOf(Literal("VAR="), Hole(0), Literal(" cmd")))
        assertEquals(RCommand(listOf("VAR" to "x y"), listOf("cmd")), node.render(mapOf(0 to "x y")))
    }

    @Test
    fun aLoneHoleExpandsToOneArgvEntryPerValue() {
        // Varargs / passthrough refs must become several arguments, not one joined blob.
        val node = parse(listOf(Literal("cmd "), Hole(0)))
        assertEquals(
            cmd("cmd", "a b", "c"),
            node.renderMulti(mapOf(0 to listOf("a b", "c"))),
        )
    }

    @Test
    fun aLoneHoleWithNoValuesDropsTheWord() {
        val node = parse(listOf(Literal("cmd "), Hole(0), Literal(" tail")))
        assertEquals(cmd("cmd", "tail"), node.renderMulti(mapOf(0 to emptyList())))
    }

    @Test
    fun rejectsACommandThatResolvesToNothing() {
        val node = parse(listOf(Hole(0)))
        assertFailsWith<MicroshellParseException> { node.render(mapOf(0 to "")) }
    }

    // --- wire format -----------------------------------------------------------------------

    @Test
    fun encodeDecodeRoundTrips() {
        val source = "A=1 cmd 'x y' @ | (b && c) ; d"
        val node = parse(listOf(Literal("A=1 cmd 'x y' "), Hole(0), Literal(" | (b && c) ; d")))
        val resolved = node.render(mapOf(0 to "a; rm -rf /"))
        assertEquals(resolved, decodePlan(resolved.encode()), "round-trip failed for `$source`")
    }

    @Test
    fun encodingSurvivesTokensThatLookLikeTags() {
        // argv entries are kernel-delimited, so a value of "cmd" or "pipe" cannot confuse the reader.
        val node = parse(listOf(Literal("echo "), Hole(0), Literal(" "), Hole(1)))
        val resolved = node.render(mapOf(0 to "pipe", 1 to "cmd"))
        assertEquals(resolved, decodePlan(resolved.encode()))
    }

    @Test
    fun rejectsMalformedPlans() {
        assertFailsWith<MicroshellParseException> { decodePlan(listOf("nope")) }
        assertFailsWith<MicroshellParseException> { decodePlan(listOf("cmd", "0")) }
        assertFailsWith<MicroshellParseException> { decodePlan(listOf("cmd", "0", "1", "a", "extra")) }
    }

    @Test
    fun encodesNestedStructureCompactly() {
        val encoded = parseText("a | b").render().encode()
        assertTrue(encoded.first() == "pipe", "expected a pipe root, got $encoded")
        assertEquals(listOf("pipe", "2", "cmd", "0", "1", "a", "cmd", "0", "1", "b"), encoded)
    }
}
