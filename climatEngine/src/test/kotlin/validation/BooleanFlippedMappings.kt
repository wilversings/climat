package validation

import com.climat.library.dslParser.dsl.decodeCliDsl
import com.climat.library.validation.validations.ValidationCode
import utils.assertContainsInMessages
import utils.getValidationMessages
import kotlin.test.Test

class BooleanFlippedMappings {
    private val toolchain = """
        root(arg1: arg, arg2: arg, arg3: flag) {
            action <% dummy command @{!arg1} %>
            sub child1() { action <% dummy2 command @{!arg2} @{!arg3} %> }
        }
    """

    @Test
    fun test() {
        val validationResults = decodeCliDsl(toolchain).getValidationMessages(ValidationCode.BooleanFlippedMappings)
        assertContainsInMessages(
            validationResults,
            "arg1",
            "arg2"
        )
    }
}
