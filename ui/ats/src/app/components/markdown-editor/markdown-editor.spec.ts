import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MarkdownEditor } from './markdown-editor';

@Component({
  imports: [MarkdownEditor, ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <app-markdown-editor>
      <mat-form-field appearance="outline">
        <mat-label>Description</mat-label>
        <textarea matInput [formControl]="control"></textarea>
        <mat-error>Required</mat-error>
      </mat-form-field>
    </app-markdown-editor>
  `,
})
class Host {
  readonly control = new FormControl('', { nonNullable: true, validators: Validators.required });
}

describe('MarkdownEditor', () => {
  let host: Host;
  let el: HTMLElement;
  let textarea: HTMLTextAreaElement;
  let fixture: ReturnType<typeof TestBed.createComponent<Host>>;

  beforeEach(async () => {
    fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    await fixture.whenStable();
    el = fixture.nativeElement;
    textarea = el.querySelector('textarea')!;
  });

  function select(value: string, start: number, end = start) {
    textarea.value = value;
    textarea.dispatchEvent(new Event('input'));
    textarea.setSelectionRange(start, end);
  }

  const button = (id: string) => el.querySelector<HTMLButtonElement>(`[data-action="${id}"]`)!;
  const toggle = (label: string) =>
    [...el.querySelectorAll<HTMLButtonElement>('mat-button-toggle button')].find((b) =>
      b.textContent?.includes(label),
    )!;
  const key = (init: KeyboardEventInit) => {
    const event = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...init });
    textarea.dispatchEvent(event);
    return event;
  };

  it('projects the form field and renders an accessible toolbar', () => {
    expect(el.querySelector('mat-form-field textarea')).toBe(textarea);
    expect(el.querySelector('[role="toolbar"]')?.getAttribute('aria-label')).toBe(
      'Text formatting',
    );
    expect(button('bold').getAttribute('aria-label')).toBe('Bold');
  });

  it('applies formatting to the selection and updates the form control', () => {
    select('hello world', 6, 11);
    button('bold').click();
    expect(textarea.value).toBe('hello **world**');
    expect(host.control.value).toBe('hello **world**');
    expect(host.control.dirty).toBe(true);
    expect([textarea.selectionStart, textarea.selectionEnd]).toEqual([8, 13]);
  });

  it('supports Ctrl+B / Cmd+I / Ctrl+K shortcuts', () => {
    select('word', 0, 4);
    expect(key({ key: 'b', ctrlKey: true }).defaultPrevented).toBe(true);
    expect(host.control.value).toBe('**word**');

    select('word', 0, 4);
    key({ key: 'i', metaKey: true });
    expect(host.control.value).toBe('_word_');

    select('site', 0, 4);
    key({ key: 'k', ctrlKey: true });
    expect(host.control.value).toBe('[site](https://)');
  });

  it('leaves other shortcuts alone', () => {
    select('word', 0, 4);
    expect(key({ key: 'a', ctrlKey: true }).defaultPrevented).toBe(false);
    expect(host.control.value).toBe('word');
  });

  it('continues lists on Enter', () => {
    select('- one', 5);
    expect(key({ key: 'Enter' }).defaultPrevented).toBe(true);
    expect(host.control.value).toBe('- one\n- ');
  });

  it('ends the list with a blank line so following text is a new paragraph', async () => {
    select('- one\n- ', 8);
    key({ key: 'Enter' });
    expect(host.control.value).toBe('- one\n\n');
  });

  it('keeps normal Enter outside lists', () => {
    select('plain', 5);
    expect(key({ key: 'Enter' }).defaultPrevented).toBe(false);
  });

  it('previews the Markdown and disables formatting while previewing', async () => {
    select('# Title\n\n**bold**', 0);
    toggle('Preview').click();
    await fixture.whenStable();
    await new Promise((r) => setTimeout(r));
    await fixture.whenStable();

    const preview = el.querySelector('.preview-pane .markdown-body')!;
    expect(preview.querySelector('h3')?.textContent).toBe('Title');
    expect(preview.querySelector('strong')?.textContent).toBe('bold');
    expect(el.querySelector<HTMLElement>('.write-pane')!.hidden).toBe(true);
    expect(button('bold').disabled).toBe(true);

    toggle('Write').click();
    await fixture.whenStable();
    expect(el.querySelector('.preview-pane')).toBeNull();
    expect(el.querySelector<HTMLElement>('.write-pane')!.hidden).toBe(false);
  });

  it('shows a placeholder when previewing empty text', async () => {
    toggle('Preview').click();
    await fixture.whenStable();
    expect(el.querySelector('.preview-empty')?.textContent).toContain('Nothing to preview');
  });

  it('switches back to Write when the form is submitted with an invalid value', async () => {
    toggle('Preview').click();
    await fixture.whenStable();
    expect(el.querySelector('.preview-pane')).not.toBeNull();

    host.control.markAsTouched();
    await fixture.whenStable();
    expect(el.querySelector('.preview-pane')).toBeNull();
    expect(el.querySelector('mat-error')?.textContent).toContain('Required');
  });

  it('does not edit a disabled field', () => {
    select('word', 0, 4);
    host.control.disable();
    button('bold').click();
    expect(textarea.value).toBe('word');
  });
});
