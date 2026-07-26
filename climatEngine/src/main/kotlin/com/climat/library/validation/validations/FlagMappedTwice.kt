package com.climat.library.validation.validations

import com.climat.library.domain.action.templateOrNull
import com.climat.library.validation.ValidationBase
import com.climat.library.validation.ValidationContext
import com.climat.library.validation.ValidationEntry
import com.climat.library.validation.ValidationResult
import com.climat.library.validation.ValidationResult.ValidationEntryType.Warning

internal class FlagMappedTwice : ValidationBase() {
    // TODO think if it should be warning or error
    override val type = Warning
    override val code = ValidationCode.FlagMappedTwice
    override fun validate(ctx: ValidationContext): Sequence<ValidationEntry> =
        ctx.toolchain.action.let { act ->
            val template = act.templateOrNull
            if (template != null) {
                template.refReferences
                    .mapNotNull { it.mapping }
                    .groupBy { it.text }
                    .values.asSequence()
                    .filter { it.size >= 2 }
                    .map { it.first() } /* TODO: more granularity: sourceMap to reference and not to the whole action */
                    .map { act.validationEntry("Mapping ${it.text} was used more than once") }
            } else { emptySequence() }
        }
}
