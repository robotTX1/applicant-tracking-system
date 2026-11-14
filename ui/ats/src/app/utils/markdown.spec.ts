import { stripMarkdown } from './markdown';

describe('stripMarkdown', () => {
  it('returns an empty string for empty input', () => {
    expect(stripMarkdown('')).toBe('');
    expect(stripMarkdown(null)).toBe('');
    expect(stripMarkdown(undefined)).toBe('');
  });

  it('removes headings, emphasis and inline code', () => {
    expect(stripMarkdown('## About **us**\nWe use `Java` and _Angular_.')).toBe(
      'About us We use Java and Angular.',
    );
  });

  it('keeps link and image text', () => {
    expect(stripMarkdown('See [our site](https://x.io) ![logo](l.png)')).toBe('See our site logo');
  });

  it('removes list markers, quotes and rules', () => {
    expect(stripMarkdown('- one\n* two\n1. three\n> quote\n---\nend')).toBe(
      'one two three quote end',
    );
  });

  it('keeps snake_case words intact', () => {
    expect(stripMarkdown('use my_var_name here')).toBe('use my_var_name here');
  });

  it('removes code fences and HTML tags', () => {
    expect(stripMarkdown('```ts\nconst a = 1;\n```\n<b>bold</b>')).toBe('const a = 1; bold');
  });
});
