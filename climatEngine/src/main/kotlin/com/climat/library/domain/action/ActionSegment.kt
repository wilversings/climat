package com.climat.library.domain.action

/**
 * A resolved `act microsh` body, as far as the engine is concerned: literal text the author wrote,
 * interleaved with the values their refs resolved to.
 *
 * The engine deliberately knows nothing about shell syntax — it does not parse the body, and the
 * two languages are kept independent. All it does is keep the boundary between *text* and *value*
 * intact, so whatever consumes this can guarantee a value is never read as syntax.
 */
sealed interface ActionSegment

/** Text written in the DSL. Whatever executes this may interpret it. */
class LiteralText(val text: String) : ActionSegment

/** One resolved ref value. Must be passed along atomically, never re-interpreted. */
class ResolvedValue(val value: String) : ActionSegment
