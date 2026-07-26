package com.climat.library.validation.validations

import com.climat.library.domain.action.templateOrNull
import com.climat.library.utils.not
import com.climat.library.validation.ValidationBase
import com.climat.library.validation.ValidationContext
import com.climat.library.validation.ValidationEntry
import com.climat.library.validation.ValidationResult
import com.climat.library.validation.ValidationResult.ValidationEntryType.Error

internal class UndefinedParams : ValidationBase() {
    override val type = Error
    override val code = ValidationCode.UndefinedParams

    override fun validate(ctx: ValidationContext): Sequence<ValidationEntry> =
        getScopeRefs(ctx).let { scopeParams ->
            val act = ctx.toolchain.action
            val template = act.templateOrNull
            if (template != null)
                template.refReferences
                    .asSequence()
                    .map { it.name }
                    .distinct()
                    .filter(not(scopeParams::contains))
                    .map { /* TODO: more granularity: sourceMap to reference and not to the whole action */
                        act.validationEntry("Parameter `$it` is not defined in the current scope")
                    }
            else emptySequence()
        }
}
