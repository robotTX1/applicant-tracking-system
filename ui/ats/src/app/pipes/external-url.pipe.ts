import { Pipe, PipeTransform } from '@angular/core';

/**
 * Normalises a user-entered website for use in `href`: adds `https://` when the scheme is missing
 * (otherwise `www.acme.com` becomes a link relative to the app) and rejects anything that isn't
 * http(s). Returns `null` for empty or unusable values.
 */
export function toExternalUrl(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  if (!trimmed) return null;
  const withScheme = /^[a-z][a-z\d+.-]*:/i.test(trimmed) ? trimmed : `https://${trimmed}`;
  try {
    const url = new URL(withScheme);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}

@Pipe({ name: 'externalUrl' })
export class ExternalUrlPipe implements PipeTransform {
  transform(value: string | null | undefined): string | null {
    return toExternalUrl(value);
  }
}
