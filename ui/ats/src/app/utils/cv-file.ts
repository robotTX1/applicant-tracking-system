/** CV upload rules, mirroring the backend's DocumentServiceImpl (PDF only, max 5 MB). */
export const MAX_CV_BYTES = 5 * 1024 * 1024;
export const CV_ACCEPT = 'application/pdf,.pdf';

/** Returns an error message for an unacceptable CV file, or null if it's fine. */
export function validateCvFile(file: File): string | null {
  const isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name);
  if (!isPdf) return 'Only PDF files are accepted.';
  if (file.size > MAX_CV_BYTES) return 'The file is larger than 5 MB.';
  if (file.size === 0) return 'The file is empty.';
  return null;
}

/** Human-readable file size: "853 B", "12 KB", "1.4 MB". */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
