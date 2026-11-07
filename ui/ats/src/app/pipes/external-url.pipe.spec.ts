import { ExternalUrlPipe, toExternalUrl } from './external-url.pipe';

describe('toExternalUrl', () => {
  it('keeps http(s) URLs', () => {
    expect(toExternalUrl('https://acme.com/jobs')).toBe('https://acme.com/jobs');
    expect(toExternalUrl('http://acme.com')).toBe('http://acme.com/');
  });

  it('adds https:// when the scheme is missing', () => {
    expect(toExternalUrl('www.morganstanley.com')).toBe('https://www.morganstanley.com/');
    expect(toExternalUrl('  acme.io/careers ')).toBe('https://acme.io/careers');
  });

  it('rejects non-http schemes and empty values', () => {
    expect(toExternalUrl('javascript:alert(1)')).toBeNull();
    expect(toExternalUrl('mailto:hr@acme.com')).toBeNull();
    expect(toExternalUrl('')).toBeNull();
    expect(toExternalUrl('   ')).toBeNull();
    expect(toExternalUrl(null)).toBeNull();
    expect(toExternalUrl(undefined)).toBeNull();
  });

  it('is exposed as a pipe', () => {
    expect(new ExternalUrlPipe().transform('acme.com')).toBe('https://acme.com/');
  });
});
