package validation

import com.climat.library.dslParser.dsl.decodeCliDsl
import com.climat.library.validation.validations.ValidationCode
import utils.assertContainsInMessages
import utils.getValidationMessages
import kotlin.test.Test

class DuplicateParamNames {

    private val toolchain = """
        root(param: flag, param: arg?) {
            action { dummy action }
            sub root_child(param1: flag, param2: arg?, param3: arg?) {
                action { dummy action 2 }
            }
            sub root_child2() {
                action { dummy action 3 }
                
                    sub root_grandchild() { action { dummy action 5 } }
                    sub root_grandchild2() { action { dummy action 5 } }
                
            }
            sub root_child3(param1: flag, param2: arg?, param1: arg?) {
                action { dummy action 5 }
            }
        }
    """

    @Test
    fun test() {
        val validationResults = decodeCliDsl(toolchain).getValidationMessages(ValidationCode.DuplicateRefNames)
        assertContainsInMessages(
            validationResults,
            "param",
            "param1"
        )
    }
}
