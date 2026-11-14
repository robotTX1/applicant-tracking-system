import { Pipe, PipeTransform } from '@angular/core';

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 60 * 60],
  ['month', 30 * 24 * 60 * 60],
  ['week', 7 * 24 * 60 * 60],
  ['day', 24 * 60 * 60],
  ['hour', 60 * 60],
  ['minute', 60],
];

const formatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

/** Formats an ISO date string relative to `now`, e.g. "3 days ago", "yesterday", "just now". */
export function formatRelativeTime(value: string | Date, now: Date = new Date()): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const seconds = Math.round((date.getTime() - now.getTime()) / 1000);
  for (const [unit, unitSeconds] of UNITS) {
    if (Math.abs(seconds) >= unitSeconds) {
      return formatter.format(Math.trunc(seconds / unitSeconds), unit);
    }
  }
  return 'just now';
}

@Pipe({ name: 'relativeTime' })
export class RelativeTimePipe implements PipeTransform {
  transform(value: string | Date | null | undefined): string {
    return value ? formatRelativeTime(value) : '';
  }
}
