import { formatRelativeTime, RelativeTimePipe } from './relative-time.pipe';

describe('formatRelativeTime', () => {
  const now = new Date('2026-11-11T12:00:00Z');
  const ago = (seconds: number) => new Date(now.getTime() - seconds * 1000).toISOString();

  it('formats recent times as "just now"', () => {
    expect(formatRelativeTime(ago(10), now)).toBe('just now');
  });

  it('picks the largest fitting unit', () => {
    expect(formatRelativeTime(ago(5 * 60), now)).toBe('5 minutes ago');
    expect(formatRelativeTime(ago(3 * 3600), now)).toBe('3 hours ago');
    expect(formatRelativeTime(ago(24 * 3600), now)).toBe('yesterday');
    expect(formatRelativeTime(ago(3 * 86400), now)).toBe('3 days ago');
    expect(formatRelativeTime(ago(14 * 86400), now)).toBe('2 weeks ago');
    expect(formatRelativeTime(ago(400 * 86400), now)).toBe('last year');
  });

  it('returns an empty string for invalid dates', () => {
    expect(formatRelativeTime('not a date', now)).toBe('');
  });
});

describe('RelativeTimePipe', () => {
  it('returns an empty string for missing values', () => {
    const pipe = new RelativeTimePipe();
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
