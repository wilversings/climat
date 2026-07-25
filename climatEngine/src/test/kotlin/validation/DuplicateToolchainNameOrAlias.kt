package validation

import com.climat.library.dslParser.dsl.decodeCliDsl
import com.climat.library.validation.validations.ValidationCode
import utils.assertContainsInMessages
import utils.getValidationMessages
import kotlin.test.Test

class DuplicateToolchainNameOrAlias {

    private val toolchain = """
        root {
            act sh { dummy action }
            
            sub root_child() { act sh { dummy action 2 } }
            sub root_child2() {
                act sh { dummy action 3 }
                
                sub root_grandchild() { act sh { dummy action 5 } }
                sub root_grandchild() { act sh { dummy action 6 } }
            }
            sub root_child() {
                act sh { dummy action 4 }
                
                sub root_grandchild() { act sh { dummy action 5 } }
                sub root_grandchild() { act sh { dummy action 6 } }
                sub root_child {
                    act sh { dummy action 6 } 
                    
                    @aliases(grand_grand_child_alias grand_grand_child_2)
                    sub grand_grand_child() {
                    }
                    
                    @alias(grand_grand_child_2)
                    sub grand_grand_child_2() {
                    }
                    
                    @alias(xylophone)
                    @aliases(grand_grand_child_3_alias grand_grand_child_3_alias)
                    sub grand_grand_child_3() {
                    }
                    
                    @aliases(xylophone)
                    sub grand_grand_child_4() {
                    }
                }
            }
        }
    """

    @Test
    fun test() {
        val validationResults =
            decodeCliDsl(toolchain).getValidationMessages(ValidationCode.DuplicateToolchainNamesOrAliases)
        assertContainsInMessages(
            validationResults,
            "root_child",
            "root_grandchild",
            "root_grandchild",
            "grand_grand_child_2",
            "grand_grand_child_3_alias",
            "xylophone"
        )
    }
}
