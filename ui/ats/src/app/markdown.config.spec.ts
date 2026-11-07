import { TestBed } from '@angular/core/testing';
import { Marked } from 'marked';
import { MarkdownService } from 'ngx-markdown';
import { markedOptionsFactory, provideAppMarkdown } from './markdown.config';

describe('markdown config', () => {
  const render = (md: string) =>
    new Marked().parse(md, { ...markedOptionsFactory(), async: false }) as string;

  it('opens links in a new tab without referrer', () => {
    expect(render('[site](https://acme.io)')).toContain(
      '<a target="_blank" rel="noopener noreferrer nofollow" href="https://acme.io">site</a>',
    );
  });

  it('shifts headings below the page h1/h2 and caps at h6', () => {
    const html = render('# One\n\n## Two\n\n###### Six');
    expect(html).toContain('<h3>One</h3>');
    expect(html).toContain('<h4>Two</h4>');
    expect(html).toContain('<h6>Six</h6>');
    expect(html).not.toMatch(/<h[12]>/);
  });

  it('keeps inline formatting inside headings and links', () => {
    expect(render('# **Bold** title')).toContain('<h3><strong>Bold</strong> title</h3>');
  });

  it('treats indented lines as text, not code blocks (pasted descriptions)', () => {
    const html = render('Responsibilities\n\n    Write code.\n    Review code.');
    expect(html).not.toContain('<pre>');
    expect(html).not.toContain('<code>');
    expect(html).toContain('Write code.');
  });

  it('still renders fenced code blocks', () => {
    expect(render('```\nconst a = 1;\n```')).toContain('<pre><code>const a = 1;');
  });

  it('turns single newlines into line breaks', () => {
    expect(render('line one\nline two')).toContain('line one<br>line two');
  });

  describe('with MarkdownService (sanitized)', () => {
    let service: MarkdownService;

    beforeEach(() => {
      TestBed.configureTestingModule({ providers: [provideAppMarkdown()] });
      service = TestBed.inject(MarkdownService);
    });

    it('strips scripts and event handlers', async () => {
      const html = await service.parse(
        '<script>alert(1)</script>\n\n<img src=x onerror="alert(1)">',
      );
      expect(html).not.toContain('<script');
      expect(html).not.toContain('onerror');
    });

    it('neutralises javascript: links', async () => {
      const html = await service.parse('[click](javascript:alert(1))');
      expect(html).not.toMatch(/href="javascript:/i);
    });

    it('keeps the new-tab link attributes after sanitizing', async () => {
      const html = await service.parse('[site](https://acme.io)');
      expect(html).toContain('target="_blank"');
      expect(html).toContain('rel="noopener noreferrer nofollow"');
    });
  });
});
