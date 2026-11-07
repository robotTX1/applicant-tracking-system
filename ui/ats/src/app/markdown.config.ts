import { Provider } from '@angular/core';
import { MARKED_OPTIONS, provideMarkdown } from 'ngx-markdown';
import { MarkedOptions, Renderer, Tokenizer } from 'marked';

export const MARKDOWN_HEADING_OFFSET = 2;

/**
 * Renderer for user-written Markdown (job descriptions):
 * - links open in a new tab and don't pass referrer/SEO credit (`noopener noreferrer nofollow`)
 * - headings are shifted down two levels so `# Title` becomes `<h3>`: the page owns `<h1>` (job
 *   title) and `<h2>` (section titles), which keeps a valid heading outline for screen readers / AXE
 *
 * HTML output is still sanitized by Angular's DomSanitizer (ngx-markdown's default), so raw
 * `<script>`, event handlers and `javascript:` URLs are stripped.
 */
export function createMarkdownRenderer(): Renderer {
  const renderer = new Renderer();
  // Regular functions calling the prototype with `this`: marked copies renderer methods onto its
  // own instance (which owns the parser), so bound methods would point at the wrong object.
  renderer.link = function (this: Renderer, token) {
    return Renderer.prototype.link
      .call(this, token)
      .replace(/^<a /, '<a target="_blank" rel="noopener noreferrer nofollow" ');
  };
  renderer.heading = function (this: Renderer, token) {
    const depth = Math.min(token.depth + MARKDOWN_HEADING_OFFSET, 6);
    return Renderer.prototype.heading.call(this, { ...token, depth });
  };
  return renderer;
}

/**
 * Disables *indented* code blocks (fenced ``` blocks still work). Descriptions are often pasted
 * from LinkedIn/Word with indented bullet lines, which Markdown would otherwise render as
 * monospace, horizontally scrolling code blocks.
 */
export function createMarkdownTokenizer(): Tokenizer {
  const tokenizer = new Tokenizer();
  tokenizer.code = () => undefined;
  return tokenizer;
}

export function markedOptionsFactory(): MarkedOptions {
  return {
    renderer: createMarkdownRenderer(),
    tokenizer: createMarkdownTokenizer(),
    gfm: true,
    breaks: true,
  };
}

export function provideAppMarkdown(): Provider[] {
  return provideMarkdown({
    markedOptions: { provide: MARKED_OPTIONS, useFactory: markedOptionsFactory },
  });
}
