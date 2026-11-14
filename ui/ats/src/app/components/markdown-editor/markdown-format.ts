/**
 * Pure Markdown editing operations for a textarea. Each takes the current text and selection and
 * returns a single replacement (`from`..`to` → `insert`) plus the selection to set afterwards, so
 * the caller can apply it in one step (keeping the browser's undo history intact).
 */

export interface TextState {
  value: string;
  start: number;
  end: number;
}

export interface TextEdit {
  from: number;
  to: number;
  insert: string;
  /** Selection in the resulting text. */
  selectionStart: number;
  selectionEnd: number;
}

export type LinePrefixKind = 'heading' | 'bullet' | 'numbered' | 'quote';

const LIST_MARKER = /^(\s*)(?:[-*+]|\d+[.)])\s+/;
const PREFIX_PATTERNS: Record<LinePrefixKind, RegExp> = {
  heading: /^#{1,6}\s+/,
  bullet: /^\s*[-*+]\s+/,
  numbered: /^\s*\d+[.)]\s+/,
  quote: /^>\s?/,
};

/** Wraps the selection in `marker` (e.g. `**`), or removes it if the selection is already wrapped. */
export function toggleWrap(state: TextState, marker: string, placeholder: string): TextEdit {
  const { value, start, end } = state;
  const selected = value.slice(start, end);
  const m = marker.length;

  // Selection includes the markers: **text** → text
  if (selected.length >= 2 * m && selected.startsWith(marker) && selected.endsWith(marker)) {
    const inner = selected.slice(m, -m);
    return {
      from: start,
      to: end,
      insert: inner,
      selectionStart: start,
      selectionEnd: start + inner.length,
    };
  }

  // Markers surround the selection: **[text]** → text
  if (value.slice(start - m, start) === marker && value.slice(end, end + m) === marker) {
    return {
      from: start - m,
      to: end + m,
      insert: selected,
      selectionStart: start - m,
      selectionEnd: start - m + selected.length,
    };
  }

  const inner = selected || placeholder;
  return {
    from: start,
    to: end,
    insert: `${marker}${inner}${marker}`,
    selectionStart: start + m,
    selectionEnd: start + m + inner.length,
  };
}

/**
 * Adds a line prefix (heading, list, quote) to every line touched by the selection, or removes it
 * if all of them already have it. Bullets and numbers replace each other.
 */
export function toggleLinePrefix(state: TextState, kind: LinePrefixKind): TextEdit {
  const { value, start, end } = state;
  const lineStart = value.lastIndexOf('\n', start - 1) + 1;
  // A selection ending right after a newline shouldn't include the next line
  const effectiveEnd = end > start && value[end - 1] === '\n' ? end - 1 : end;
  const nextNewline = value.indexOf('\n', effectiveEnd);
  const lineEnd = nextNewline === -1 ? value.length : nextNewline;

  const lines = value.slice(lineStart, lineEnd).split('\n');
  const pattern = PREFIX_PATTERNS[kind];
  const contentLines = lines.filter((l) => l.trim() !== '');
  const remove = contentLines.length > 0 && contentLines.every((l) => pattern.test(l));

  let n = 0;
  const updated = lines.map((line) => {
    if (remove) return line.replace(pattern, '');
    if (line.trim() === '' && lines.length > 1) return line;
    const base = kind === 'bullet' || kind === 'numbered' ? line.replace(LIST_MARKER, '$1') : line;
    switch (kind) {
      case 'heading':
        return `## ${base.replace(PREFIX_PATTERNS.heading, '')}`;
      case 'bullet':
        return `- ${base}`;
      case 'numbered':
        return `${++n}. ${base}`;
      case 'quote':
        return `> ${base}`;
    }
  });

  const insert = updated.join('\n');
  if (start === end && lines.length === 1) {
    const caret = Math.max(lineStart, start + insert.length - (lineEnd - lineStart));
    return { from: lineStart, to: lineEnd, insert, selectionStart: caret, selectionEnd: caret };
  }
  return {
    from: lineStart,
    to: lineEnd,
    insert,
    selectionStart: lineStart,
    selectionEnd: lineStart + insert.length,
  };
}

/** Inserts `[text](url)`, selecting the part the user most likely wants to type next. */
export function insertLink(state: TextState): TextEdit {
  const { value, start, end } = state;
  const selected = value.slice(start, end);

  if (/^https?:\/\/\S+$/i.test(selected)) {
    const text = 'link text';
    return {
      from: start,
      to: end,
      insert: `[${text}](${selected})`,
      selectionStart: start + 1,
      selectionEnd: start + 1 + text.length,
    };
  }

  const text = selected || 'link text';
  const url = 'https://';
  const urlStart = start + text.length + 3; // [ + text + ](
  return {
    from: start,
    to: end,
    insert: `[${text}](${url})`,
    selectionStart: urlStart,
    selectionEnd: urlStart + url.length,
  };
}

/** Inline code for single-line selections, a fenced block for multi-line ones. */
export function toggleCode(state: TextState): TextEdit {
  const { value, start, end } = state;
  const selected = value.slice(start, end);
  if (!selected.includes('\n')) return toggleWrap(state, '`', 'code');

  const before = start > 0 && value[start - 1] !== '\n' ? '\n' : '';
  const after = end < value.length && value[end] !== '\n' ? '\n' : '';
  const opening = `${before}\`\`\`\n`;
  return {
    from: start,
    to: end,
    insert: `${opening}${selected}\n\`\`\`${after}`,
    selectionStart: start + opening.length,
    selectionEnd: start + opening.length + selected.length,
  };
}

/**
 * Enter inside a list item: continues the list with the next marker, or ends the list when the
 * current item is empty. Returns `null` when Enter should behave normally.
 */
export function continueList(state: TextState): TextEdit | null {
  const { value, start, end } = state;
  if (start !== end) return null;

  const lineStart = value.lastIndexOf('\n', start - 1) + 1;
  const nextNewline = value.indexOf('\n', start);
  const lineEnd = nextNewline === -1 ? value.length : nextNewline;
  const line = value.slice(lineStart, lineEnd);

  const match = /^(\s*)(?:([-*+])|(\d+)([.)]))(\s+)/.exec(line);
  if (!match || start < lineStart + match[0].length) return null;

  const [marker, indent, bullet, number, separator, spacing] = match;
  if (line.slice(marker.length).trim() === '') {
    // Empty item: end the list, leaving a blank line. Without it Markdown treats the next line as
    // a continuation of the previous item ("lazy continuation").
    return {
      from: lineStart,
      to: lineEnd,
      insert: '\n',
      selectionStart: lineStart + 1,
      selectionEnd: lineStart + 1,
    };
  }

  const next = bullet ?? `${Number(number) + 1}${separator}`;
  const insert = `\n${indent}${next}${spacing}`;
  return {
    from: start,
    to: start,
    insert,
    selectionStart: start + insert.length,
    selectionEnd: start + insert.length,
  };
}
