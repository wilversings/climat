parser grammar DslParser;
options { tokenVocab=DslLexer; }

root: docstring? rootModifiers* IDENTIFIER (LPAREN params? RPAREN)? rootBody EOF;
rootModifiers: MOD_SEAL | MOD_ALLOW_UNMATCHED | aliasModifier | aliasesModifier;
rootBody: LCURLY rootStatements* RCURLY;
rootStatements: sub | action | constDef;

sub: docstring? subModifiers* SUB IDENTIFIER (LPAREN params? RPAREN)? subBody;
// TODO implement functionality for SEALED and SHIFTED modifiers
subModifiers: rootModifiers | MOD_SHIFT;
aliasModifier: MOD_ALIAS LPAREN IDENTIFIER RPAREN;
aliasesModifier: MOD_ALIASES LPAREN IDENTIFIER+ RPAREN;
params: param (COMMA param)* COMMA?;
param: IDENTIFIER paramShort? COLON paramType;
paramType: FLAG | argument;
argument: ARGUMENT (QMARK)? (EQ literal)?;
paramShort: IDENTIFIER;

subBody: LCURLY subStatements* RCURLY;
subStatements: rootStatements;

action: shellAction | javascriptAction | SCOPE_PARAMS;

shellAction: ActionTemplate_BEGIN actionTemplateEntry* ActionTemplate_CLOSE;
// The script is split into several tokens whenever it contains braces that don't close the body
javascriptAction: CustomScript_JAVASCRIPT_BEGIN CustomScript_SCRIPT* CustomScript_END;

constDef: CONST IDENTIFIER EQ literal;

literal: stringTemplate | booleanLiteral;
booleanLiteral: TRUE | FALSE;

stringTemplate: DOUBLE_QUOTE stringTemplateEntry* StringTemplate_CLOSE;
stringTemplateEntry: stringTemplateContent | stringTemplateInterpolation;
stringTemplateContent: StringTemplate_CONTENT | StringTemplate_AT;
stringTemplateInterpolation: StringTemplate_Interpolation_OPEN Interpolation_NEGATE? Interpolation_IDENTIFIER mapping? Interpolation_CLOSE;

actionTemplateEntry: actionTemplateContent | actionTemplateInterpolation;
actionTemplateContent: ActionTemplate_CONTENT | ActionTemplate_AT;
actionTemplateInterpolation: ActionTemplate_Interpolation_OPEN Interpolation_NEGATE? Interpolation_IDENTIFIER mapping? Interpolation_CLOSE;

mapping: Interpolation_QMARK Interpolation_MAPPING_OPEN mappingTemplateEntry* MappingTemplate_CLOSE;
mappingTemplateEntry: MappingTemplate_CONTENT | MappingTemplate_LCURLY | MappingTemplate_PLACEHOLDER;

docstring: Docstring_BEGIN docstringEntry* Docstring_END;
docstringEntry: Docstring_CONTENT | paramTag;
paramTag: Docstring_AT_PARAM Docstring_IDENTIFIER;

