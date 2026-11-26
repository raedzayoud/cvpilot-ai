/** Saves a downloaded Blob under the given name. */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Revoking synchronously can cancel the download in Firefox/Safari; give the browser time to start it.
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}
