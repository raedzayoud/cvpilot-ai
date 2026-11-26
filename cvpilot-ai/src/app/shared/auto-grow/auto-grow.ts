import { AfterViewChecked, Directive, ElementRef, HostListener, inject } from '@angular/core';

/**
 * Grows a textarea to fit its content (no inner scrollbar). Works in every browser, unlike the
 * CSS `field-sizing: content` property. Re-measures when the user types and when the bound value
 * is replaced programmatically (e.g. a server update).
 */
@Directive({ selector: 'textarea[appAutoGrow]' })
export class AutoGrow implements AfterViewChecked {
  private readonly element = inject<ElementRef<HTMLTextAreaElement>>(ElementRef).nativeElement;
  private lastValue: string | null = null;
  private lastWidth = 0;

  @HostListener('input')
  protected onInput(): void {
    this.resize();
  }

  ngAfterViewChecked(): void {
    // Cheap guard: only measure when the text or the available width changed.
    const width = this.element.clientWidth;
    if (this.element.value !== this.lastValue || width !== this.lastWidth) this.resize();
  }

  private resize(): void {
    const el = this.element;
    this.lastValue = el.value;
    this.lastWidth = el.clientWidth;
    el.style.height = 'auto';
    const border = el.offsetHeight - el.clientHeight;
    el.style.height = `${el.scrollHeight + border}px`;
  }
}
