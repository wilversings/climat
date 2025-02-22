package com.climat.library.validation.validations

import com.climat.library.validation.ValidationBase
import com.climat.library.validation.ValidationContext
import com.climat.library.validation.ValidationEntry
import com.climat.library.validation.ValidationResult.ValidationEntryType.Warning

internal class AncestorSubcommandWithSameName : ValidationBase() {
    override val type = Warning
    override val code = ValidationCode.AncestorSubcommandWithSameName

    override fun validate(ctx: ValidationContext): Sequence<ValidationEntry> =
        ctx.traceToRoot
            .find { it.name == ctx.toolchain.name }
            ?.let {
                sequenceOf(
                    it.validationEntry("There is already an ancestor with name ${it.name}")
                )
            }.orEmpty()
}
