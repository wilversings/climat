import siteConfig from '@generated/docusaurus.config';
export default function prismIncludeLanguages(PrismObject) {
  const {
    themeConfig: {prism},
  } = siteConfig;
  const {additionalLanguages} = prism;
  // Prism components work on the Prism instance on the window, while prism-
  // react-renderer uses its own Prism instance. We temporarily mount the
  // instance onto window, import components to enhance it, then remove it to
  // avoid polluting global namespace.
  // You can mutate PrismObject: registering plugins, deleting languages... As
  // long as you don't re-assign it
  globalThis.Prism = PrismObject;
  additionalLanguages.forEach((lang) => {
    // eslint-disable-next-line global-require, import/no-dynamic-require
    require(`prismjs/components/prism-${lang}`);
  });

  // `@{name}` / `@{!name}` / `@{name ? "--opt={}"}`, shared by string and action templates.
  // Every alternation below is disjoint on its first character, so a pattern that fails to match
  // does so in linear time — no backtracking blowup on a template that is never terminated.
  const interpolation = {
    pattern: /@\{\s*!?\s*[\w-]+(?:\s*\?\s*"(?:\\.|[^\\"])*")?\s*\}/,
    greedy: true,
    inside: {
      'punctuation': /^@\{|\}$/,
      'mapping': {
        pattern: /"(?:\\.|[^\\"])*"/,
        alias: 'string',
        inside: {
          // `{}` is the value placeholder; `\{}` is a literal one
          'placeholder': {
            pattern: /(^|[^\\])\{\}/,
            lookbehind: true,
            alias: 'variable'
          },
          'escape': {
            pattern: /\\./,
            alias: 'char'
          }
        }
      },
      'operator': /[!?]/,
      'variable': /[\w-]+/
    }
  };

  // An action body opens with a run of `{` and closes with an equally long run of `}`. A regex
  // can't count, so the delimiter widths are spelled out widest-first; three is plenty for docs.
  const actionBody = [3, 2, 1]
    .map((n) => {
      // Body content is anything but a `}` run long enough to close it
      const content = `(?:\\\\[\\s\\S]|[^\\\\}]|\\}(?!\\}{${n - 1}}))*`;
      return `\\{{${n}}${content}\\}{${n}}`;
    })
    .join('|');

  const actionTemplate = {
    pattern: new RegExp(`((?:^|[^\\w-])(?:javascript\\s+)?action\\s*)(?:${actionBody})`),
    lookbehind: true,
    greedy: true,
    alias: 'string',
    inside: {
      'punctuation': /^\{+|\}+$/,
      'interpolation': interpolation,
      'escape': {
        pattern: /\\./,
        alias: 'char'
      }
    }
  };

  // Identifiers may contain `-`, so `\b` is too weak a boundary: it would light up the `arg` in
  // `my-arg`.
  const keyword = {
    pattern: /(^|[^\w-])(?:action|arg|const|default|false|flag|javascript|override|params|scope|sub|true)(?![\w-])/,
    lookbehind: true,
    alias: 'keyword'
  };

  PrismObject.languages.clidsl = {
    'comment': {
      pattern: /\/\/.*|\/\*[\s\S]*?(?:\*\/|$)/,
      greedy: true
    },
    'doc': {
      pattern: /"""[\s\S]*?(?:"""|$)/,
      greedy: true,
      alias: 'comment',
      inside: {
        'annotation': /@param(?![\w-])/
      }
    },
    'action-template': actionTemplate,
    'string': {
      pattern: /"(?:\\.|[^\\"\r\n])*"/,
      greedy: true,
      inside: {
        'interpolation': interpolation,
        'escape': {
          pattern: /\\./,
          alias: 'char'
        }
      }
    },
    'modifier': {
      pattern: /@(?:seal|shift|aliases|alias|allow-unmatched)(?![\w-])/,
      alias: 'keyword'
    },
    'keyword': keyword,
    'punctuation': /[{}()[\],:=?!]/
  }

  delete globalThis.Prism;
}
