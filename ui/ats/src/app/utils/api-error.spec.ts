import { HttpErrorResponse } from '@angular/common/http';
import { getApiErrorDetails, getApiErrorTitle } from './api-error';

describe('api-error', () => {
  const apiError = (error: unknown) => new HttpErrorResponse({ status: 400, error });

  it('reads details and title from the backend ErrorResponse', () => {
    const err = apiError({
      type: 'about:blank',
      title: 'Bad Request',
      details: 'Name is taken',
      status: '400',
    });
    expect(getApiErrorDetails(err)).toBe('Name is taken');
    expect(getApiErrorTitle(err)).toBe('Bad Request');
  });

  it('ignores the RFC 7807 "detail" spelling the backend does not use', () => {
    expect(getApiErrorDetails(apiError({ detail: 'x' }))).toBeNull();
  });

  it('returns null for missing, blank or non-string values and non-HTTP errors', () => {
    expect(getApiErrorDetails(apiError(null))).toBeNull();
    expect(getApiErrorDetails(apiError('plain text body'))).toBeNull();
    expect(getApiErrorDetails(apiError({ details: '  ' }))).toBeNull();
    expect(getApiErrorDetails(apiError({ details: 42 }))).toBeNull();
    expect(getApiErrorDetails(new Error('boom'))).toBeNull();
    expect(getApiErrorTitle(new HttpErrorResponse({ status: 0 }))).toBeNull();
  });
});
