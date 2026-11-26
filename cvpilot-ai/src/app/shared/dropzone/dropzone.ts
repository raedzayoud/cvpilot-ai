import { Component, input, output, signal } from '@angular/core';

@Component({
  selector: 'app-dropzone',
  templateUrl: './dropzone.html',
  styleUrl: './dropzone.css',
})
export class Dropzone {
  accept = input('.pdf,.docx,.tex');
  title = input('Drop your file here');
  hint = input('PDF, DOCX or LaTeX, up to 10 MB');
  compact = input(false);
  fileSelected = output<File>();

  protected readonly dragging = signal(false);
  protected readonly error = signal<string | null>(null);

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.acceptFile(event.dataTransfer?.files.item(0) ?? null);
  }

  protected onChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.acceptFile(input.files?.item(0) ?? null);
    input.value = '';
  }

  private acceptFile(file: File | null): void {
    if (!file) return;
    const allowed = this.accept()
      .split(',')
      .map((ext) => ext.trim().toLowerCase())
      .filter(Boolean);
    if (!allowed.some((ext) => file.name.toLowerCase().endsWith(ext))) {
      const names = allowed.map((ext) => ext.replace('.', '').toUpperCase());
      this.error.set(`Choose a ${names.join(', ').replace(/, ([^,]+)$/, ' or $1')} file.`);
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.error.set('That file is larger than 10 MB.');
      return;
    }
    this.error.set(null);
    this.fileSelected.emit(file);
  }
}
