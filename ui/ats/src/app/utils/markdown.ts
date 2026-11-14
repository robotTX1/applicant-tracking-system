/**
 * Turns Markdown into a single line of plain text for previews (e.g. job cards).
 * Not a full parser — it removes the common syntax so snippets don't show `**`, `#`, `[]()` etc.
 */
export function stripMarkdown(markdown: string | null | undefined): string {
  if (!markdown) return '';
  return (
    markdown
      // fenced code blocks: keep the code, drop the fences
      .replace(/```[^\n]*\n?/g, '')
      // images ![alt](url) -> alt, links [text](url) -> text
      .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
      .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
      // headings, blockquotes, list markers at line start
      .replace(/^\s{0,3}#{1,6}\s+/gm, '')
      .replace(/^\s{0,3}>\s?/gm, '')
      .replace(/^\s*(?:[-*+]|\d+[.)])\s+/gm, '')
      // horizontal rules
      .replace(/^\s*(?:[-*_]\s*){3,}$/gm, '')
      // emphasis / strikethrough / inline code
      .replace(/\*\*(.*?)\*\*/g, '$1')
      .replace(/(^|\W)__(.+?)__(?!\w)/g, '$1$2')
      .replace(/\*(.*?)\*/g, '$1')
      // underscores only count as emphasis at word boundaries, so snake_case survives
      .replace(/(^|\W)_(.+?)_(?!\w)/g, '$1$2')
      .replace(/~~(.*?)~~/g, '$1')
      .replace(/`([^`]*)`/g, '$1')
      // HTML tags
      .replace(/<[^>]+>/g, '')
      .replace(/\s+/g, ' ')
      .trim()
  );
}
