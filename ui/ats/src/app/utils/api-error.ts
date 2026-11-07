import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from '../api';

/**
 * Returns the human-readable `details` from the backend's {@link ErrorResponse} body, or `null`
 * when the error isn't an API error with details (network failure, unexpected body, etc.).
 */
export function getApiErrorDetails(err: unknown): string | null {
  if (!(err instanceof HttpErrorResponse)) return null;
  const details = (err.error as Partial<ErrorResponse> | null)?.details;
  return typeof details === 'string' && details.trim() ? details : null;
}

/** Same as {@link getApiErrorDetails}, but for the error `title` (e.g. "Bad Request"). */
export function getApiErrorTitle(err: unknown): string | null {
  if (!(err instanceof HttpErrorResponse)) return null;
  const title = (err.error as Partial<ErrorResponse> | null)?.title;
  return typeof title === 'string' && title.trim() ? title : null;
}
