import { MAX_CV_BYTES, formatFileSize, validateCvFile } from './cv-file';

const pdf = (name = 'cv.pdf', size = 1024) =>
  new File([new Uint8Array(size)], name, { type: 'application/pdf' });

describe('validateCvFile', () => {
  it('accepts PDFs up to 5 MB', () => {
    expect(validateCvFile(pdf())).toBeNull();
    expect(validateCvFile(pdf('cv.pdf', MAX_CV_BYTES))).toBeNull();
  });

  it('accepts .pdf files with a missing MIME type', () => {
    expect(validateCvFile(new File(['x'], 'CV.PDF'))).toBeNull();
  });

  it('rejects other types, oversized and empty files', () => {
    expect(validateCvFile(new File(['x'], 'cv.docx', { type: 'application/msword' }))).toContain(
      'Only PDF',
    );
    expect(validateCvFile(pdf('cv.pdf', MAX_CV_BYTES + 1))).toContain('larger than 5 MB');
    expect(validateCvFile(pdf('cv.pdf', 0))).toContain('empty');
  });
});

describe('formatFileSize', () => {
  it('uses bytes, KB or MB depending on the size', () => {
    expect(formatFileSize(853)).toBe('853 B');
    expect(formatFileSize(12_400)).toBe('12 KB');
    expect(formatFileSize(1.4 * 1024 * 1024)).toBe('1.4 MB');
  });
});
