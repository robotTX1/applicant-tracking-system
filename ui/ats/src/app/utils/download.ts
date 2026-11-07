/** Saves a Blob as a file via a temporary object URL and anchor click. */
export function saveBlob(doc: Document, blob: Blob, fileName: string): void {
  const view = doc.defaultView;
  if (!view) return;
  const url = view.URL.createObjectURL(blob);
  const anchor = doc.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.rel = 'noopener';
  anchor.style.display = 'none';
  doc.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Revoke after the click has been handled so the download can start
  view.setTimeout(() => view.URL.revokeObjectURL(url), 0);
}
