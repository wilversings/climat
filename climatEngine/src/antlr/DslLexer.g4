// DELETE THIS CONTENT IF YOU PUT COMBINED GRAMMAR IN Parser TAB
lexer grammar DslLexer;

channels {
  WHITESPACE_CHANNEL,
  COMMENT
}

@members {
    // Number of `{` characters that opened the action body currently being lexed. The body ends at
    // the first run of exactly that many `}` characters, so `act sh {{ ... }}` may contain lone
    // braces (shell blocks, `${VAR}`, JavaScript bodies) without any escaping.
    private var actionBraceCount = 0

    private fun openActionBody() {
        actionBraceCount = text.count { it == '{' }
    }

    private fun atActionBodyEnd(): Boolean = text.length == actionBraceCount
}

EQ : '=' ;
COMMA : ',' ;
COLON : ':' ;
LPAREN : '(' ;
RPAREN : ')' ;
LCURLY : '{' ;
RCURLY : '}' ;
LBRAKET: '[';
RBRAKET: ']';
DOUBLE_QUOTE: '"' -> pushMode(StringTemplate);
QMARK: '?';
CONST: 'const';
TRUE: 'true';
FALSE: 'false';
ACT: 'act';
SH: 'sh';
JS: 'js';
MICROSH: 'microsh';

// Parameters
FLAG: 'flag';
ARGUMENT: 'arg';

// Default
OVERRIDE: 'override';
DEFAULT: 'default';

// Sub
MOD_SEAL: '@seal';
MOD_SHIFT: '@shift';
MOD_ALIAS: '@alias';
MOD_ALIASES: '@aliases';
MOD_ALLOW_UNMATCHED: '@allow-unmatched';
SUB: 'sub';

// Props
// An action body opens with a run of one or more `{` and closes with an equally long run of `}`.
ActionTemplate_BEGIN: ACT WS* SH WS* LCURLY+ {openActionBody();} -> pushMode(ActionTemplate);
CustomScript_JS_BEGIN: ACT WS* JS WS* LCURLY+ {openActionBody();} -> pushMode(CustomScript);
// The embedded microshell reuses the ActionTemplate mode verbatim: the body is lexed into the same
// content/interpolation tokens, and the microshell parser takes it from there.
MicroshellTemplate_BEGIN: ACT WS* MICROSH WS* LCURLY+ {openActionBody();} -> pushMode(ActionTemplate);

// Comments
MULTILINE_COMMENT: '/*' .*? '*/' -> channel(COMMENT);
SINGLELINE_COMMENT: '//' .*? '\n' -> channel(COMMENT);

// Actions
ACT_SCOPE_PARAMS: ACT WS* 'scope-params';

// Docstring
Docstring_BEGIN: '"""' -> pushMode(Docstring);

WS: [ \t\n\r\f]+ -> channel(WHITESPACE_CHANNEL);
fragment ALPHANUMERIC: [a-zA-Z0-9];
IDENTIFIER: (ALPHANUMERIC | [_-])+;

mode StringTemplate;
StringTemplate_Interpolation_OPEN: '@' LCURLY -> pushMode(Interpolation);
// A lone `@` (not starting an `@{` interpolation) is plain content
StringTemplate_AT: '@';
StringTemplate_CONTENT: ('\\@' | '\\"' | ~[@"])+;
StringTemplate_CLOSE: '"' -> popMode;

mode ActionTemplate;
ActionTemplate_Interpolation_OPEN: '@' LCURLY -> pushMode(Interpolation);
// A lone `@` (not starting an `@{` interpolation) is plain content
ActionTemplate_AT: '@';
// A run of `}` only closes the body when it is exactly as long as the opening run of `{`;
// any other run is content, emitted one brace at a time.
ActionTemplate_CLOSE: RCURLY+ {atActionBodyEnd()}? -> popMode;
ActionTemplate_CONTENT: ('\\@' | '\\}' | ~[@}])+ | RCURLY;

mode Interpolation;
Interpolation_IDENTIFIER: IDENTIFIER;
Interpolation_QMARK: QMARK;
Interpolation_MAPPING_OPEN: '"' -> pushMode(MappingTemplate);
Interpolation_CLOSE: RCURLY -> popMode;
Interpolation_WS: WS -> channel(WHITESPACE_CHANNEL);
Interpolation_NEGATE: '!';

// The `"..."` text of a conditional mapping (`@{x ? "--today-is={}"}`). `{}` is the placeholder
// for the ref's value; write a literal one by escaping the brace (`\{}`).
mode MappingTemplate;
MappingTemplate_PLACEHOLDER: LCURLY RCURLY;
MappingTemplate_CONTENT: ('\\"' | '\\{' | ~["{])+;
MappingTemplate_LCURLY: LCURLY;
MappingTemplate_CLOSE: '"' -> popMode;

mode Docstring;
Docstring_AT_PARAM: '@param' WS -> pushMode(DocstringRef);
Docstring_CONTENT: ~[@"]+;
Docstring_END: '"""' -> popMode;

mode DocstringRef;
Docstring_IDENTIFIER: IDENTIFIER WS -> popMode;

mode CustomScript;
CustomScript_END: RCURLY+ {atActionBodyEnd()}? -> popMode;
CustomScript_SCRIPT: ('\\}' | ~'}')+ | RCURLY;
