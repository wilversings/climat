package com.climat.library.validation.validations

import com.climat.library.domain.action.templateOrNull
import com.climat.library.domain.ref.ArgDefinition
import com.climat.library.validation.ValidationBase
import com.climat.library.validation.ValidationContext
import com.climat.library.validation.ValidationEntry
import com.climat.library.validation.ValidationResult
import com.climat.library.validation.ValidationResult.ValidationEntryType.Error

internal class BooleanFlippedMappings : ValidationBase() {
    override val type = Error
    override val code = ValidationCode.BooleanFlippedMappings

    override fun validate(ctx: ValidationContext): Sequence<ValidationEntry> =
        ctx.toolchain.action.let { act ->
            val template = act.templateOrNull
            if (template != null) {
                getScopeRefs(ctx)
                    .values
                    .map { it.last() }
                    .filterIsInstance<ArgDefinition>()
                    .map { it.name }
                    .intersect(
                        template.refReferences
                            .filter { it.isFlipped }
                            .map { it.name }
                            .toSet()
                    ).map { /* TODO: more granularity: sourceMap to reference and not to the whole action */
                        act.validationEntry("Param `$it` cannot be flipped because it is not a flag")
                    }.asSequence()
            } else {
                emptySequence()
            }
        }
}
