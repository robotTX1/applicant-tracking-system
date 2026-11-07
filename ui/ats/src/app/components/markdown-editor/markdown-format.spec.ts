import {
  continueList,
  insertLink,
  TextEdit,
  TextState,
  toggleCode,
  toggleLinePrefix,
  toggleWrap,
} from './markdown-format';

/** `|` marks a caret, `[` … `]` a selection. */
function parse(marked: string): TextState {
  const caret = marked.indexOf('|');
  if (caret !== -1) {
    const value = marked.replace('|', '');
    return { value, start: caret, end: caret };
  }
  const start = marked.indexOf('[');
  const end = marked.indexOf(']') - 1;
  return { value: marked.replace('[', '').replace(']', ''), start, end };
}

/** Applies the edit and renders the result with the same markers. */
function apply(state: TextState, edit: TextEdit | null): string {
  if (!edit) return 'null';
  const value = state.value.slice(0, edit.from) + edit.insert + state.value.slice(edit.to);
  if (edit.selectionStart === edit.selectionEnd) {
    return value.slice(0, edit.selectionStart) + '|' + value.slice(edit.selectionStart);
  }
  return (
    value.slice(0, edit.selectionStart) +
    '[' +
    value.slice(edit.selectionStart, edit.selectionEnd) +
    ']' +
    value.slice(edit.selectionEnd)
  );
}

const run = (marked: string, fn: (s: TextState) => TextEdit | null) => {
  const state = parse(marked);
  return apply(state, fn(state));
};

describe('toggleWrap', () => {
  const bold = (s: TextState) => toggleWrap(s, '**', 'bold text');

  it('wraps the selection and keeps it selected', () => {
    expect(run('a [word] b', bold)).toBe('a **[word]** b');
  });

  it('inserts a selected placeholder at the caret', () => {
    expect(run('a | b', bold)).toBe('a **[bold text]** b');
  });

  it('unwraps when the markers surround the selection', () => {
    expect(run('a **[word]** b', bold)).toBe('a [word] b');
  });

  it('unwraps when the selection includes the markers', () => {
    expect(run('a [**word**] b', bold)).toBe('a [word] b');
  });

  it('italic with _ does not confuse bold', () => {
    expect(run('[**word**]', (s) => toggleWrap(s, '_', 'x'))).toBe('_[**word**]_');
  });
});

describe('toggleLinePrefix', () => {
  it('adds a bullet to the current line keeping the caret position', () => {
    expect(run('one\ntw|o', (s) => toggleLinePrefix(s, 'bullet'))).toBe('one\n- tw|o');
  });

  it('removes the bullet when the line already has one', () => {
    expect(run('- tw|o', (s) => toggleLinePrefix(s, 'bullet'))).toBe('tw|o');
  });

  it('numbers every selected line and skips blank lines', () => {
    expect(run('[a\nb\n\nc]', (s) => toggleLinePrefix(s, 'numbered'))).toBe('[1. a\n2. b\n\n3. c]');
  });

  it('switches bullets to numbers', () => {
    expect(run('[- a\n- b]', (s) => toggleLinePrefix(s, 'numbered'))).toBe('[1. a\n2. b]');
  });

  it('removes numbering when all lines are numbered', () => {
    expect(run('[1. a\n2. b]', (s) => toggleLinePrefix(s, 'numbered'))).toBe('[a\nb]');
  });

  it('turns a line into a heading and replaces an existing heading level', () => {
    expect(run('Tit|le', (s) => toggleLinePrefix(s, 'heading'))).toBe('## Tit|le');
    expect(run('[## Title]', (s) => toggleLinePrefix(s, 'heading'))).toBe('[Title]');
  });

  it('does not include the next line when the selection ends with a newline', () => {
    expect(run('[a\n]b', (s) => toggleLinePrefix(s, 'quote'))).toBe('[> a]\nb');
  });
});

describe('insertLink', () => {
  it('uses the selection as link text and selects the URL', () => {
    expect(run('see [our site] now', insertLink)).toBe('see [our site]([https://]) now');
  });

  it('uses a selected URL as target and selects the text', () => {
    expect(run('[https://acme.io]', insertLink)).toBe('[[link text]](https://acme.io)');
  });

  it('inserts a placeholder at the caret', () => {
    expect(run('|', insertLink)).toBe('[link text]([https://])');
  });
});

describe('toggleCode', () => {
  it('uses inline code for single-line selections', () => {
    expect(run('run [npm test]', toggleCode)).toBe('run `[npm test]`');
  });

  it('uses a fenced block on its own lines for multi-line selections', () => {
    expect(run('x [a\nb] y', toggleCode)).toBe('x \n```\n[a\nb]\n```\n y');
  });
});

describe('continueList', () => {
  it('continues a bullet list', () => {
    expect(run('- one|', continueList)).toBe('- one\n- |');
  });

  it('increments numbered lists and keeps the separator and indent', () => {
    expect(run('  3) three|', continueList)).toBe('  3) three\n  4) |');
  });

  it('ends the list on an empty item, leaving a blank line so the next paragraph is separate', () => {
    expect(run('- one\n- |', continueList)).toBe('- one\n\n|');
  });

  it('splits an item when Enter is pressed mid-text', () => {
    expect(run('- on|e', continueList)).toBe('- on\n- |e');
  });

  it('does nothing outside lists, inside the marker, or with a selection', () => {
    expect(run('plain|', continueList)).toBe('null');
    expect(run('-| one', continueList)).toBe('null');
    expect(run('- [one]', continueList)).toBe('null');
  });
});
