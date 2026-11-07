import {
  Component,
  contentChild,
  DOCUMENT,
  effect,
  ElementRef,
  inject,
  signal,
} from '@angular/core';
import { NgControl } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MarkdownComponent } from 'ngx-markdown';
import { provideAppMarkdown } from '../../markdown.config';
import {
  continueList,
  insertLink,
  LinePrefixKind,
  TextEdit,
  TextState,
  toggleCode,
  toggleLinePrefix,
  toggleWrap,
} from './markdown-format';

export type MarkdownEditorMode = 'write' | 'preview';

interface ToolbarAction {
  id: string;
  icon: string;
  label: string;
  shortcut?: string;
  run: (state: TextState) => TextEdit;
}

const linePrefix = (kind: LinePrefixKind) => (s: TextState) => toggleLinePrefix(s, kind);

/**
 * Markdown toolbar + Write/Preview toggle around a projected Material form field:
 *
 * ```html
 * <app-markdown-editor>
 *   <mat-form-field appearance="outline">
 *     <mat-label>Description</mat-label>
 *     <textarea matInput formControlName="description" cdkTextareaAutosize></textarea>
 *   </mat-form-field>
 * </app-markdown-editor>
 * ```
 *
 * The textarea stays a plain `matInput` bound to the form, so label, hints, errors, validators and
 * dirty tracking keep working unchanged; the editor only edits its text.
 */
@Component({
  selector: 'app-markdown-editor',
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatTooltipModule,
    MarkdownComponent,
  ],
  providers: [provideAppMarkdown()],
  // Keyboard shortcuts bubble up from the projected textarea (filtered in onKeydown)
  host: { '(keydown)': 'onKeydown($event)' },
  templateUrl: './markdown-editor.html',
  styleUrl: './markdown-editor.scss',
})
export class MarkdownEditor {
  private readonly document = inject(DOCUMENT);
  private readonly textareaRef = contentChild.required(MatInput, {
    read: ElementRef<HTMLTextAreaElement>,
  });
  private readonly ngControl = contentChild(NgControl);

  readonly mode = signal<MarkdownEditorMode>('write');
  /** Current Markdown, kept in sync with the form control for the preview. */
  readonly value = signal('');

  readonly actions: ToolbarAction[][] = [
    [
      {
        id: 'bold',
        icon: 'format_bold',
        label: 'Bold',
        shortcut: 'Ctrl+B',
        run: (s) => toggleWrap(s, '**', 'bold text'),
      },
      {
        id: 'italic',
        icon: 'format_italic',
        label: 'Italic',
        shortcut: 'Ctrl+I',
        run: (s) => toggleWrap(s, '_', 'italic text'),
      },
      { id: 'heading', icon: 'title', label: 'Heading', run: linePrefix('heading') },
    ],
    [
      {
        id: 'bullet',
        icon: 'format_list_bulleted',
        label: 'Bulleted list',
        run: linePrefix('bullet'),
      },
      {
        id: 'numbered',
        icon: 'format_list_numbered',
        label: 'Numbered list',
        run: linePrefix('numbered'),
      },
      { id: 'quote', icon: 'format_quote', label: 'Quote', run: linePrefix('quote') },
    ],
    [
      { id: 'link', icon: 'link', label: 'Link', shortcut: 'Ctrl+K', run: insertLink },
      { id: 'code', icon: 'code', label: 'Code', run: toggleCode },
    ],
  ];

  private readonly shortcuts: Record<string, ToolbarAction> = Object.fromEntries(
    this.actions
      .flat()
      .filter((a) => a.shortcut)
      .map((a) => [a.shortcut!.slice(-1).toLowerCase(), a]),
  );

  constructor() {
    effect((onCleanup) => {
      const control = this.ngControl()?.control;
      if (!control) return;
      this.value.set(control.value ?? '');
      const sub = control.events.subscribe(() => {
        this.value.set(control.value ?? '');
        // Validation errors live in the (hidden) form field: if the form is submitted while
        // previewing an invalid value, switch back so the error is visible.
        if (this.mode() === 'preview' && control.invalid && control.touched) this.mode.set('write');
      });
      onCleanup(() => sub.unsubscribe());
    });
  }

  /** Focus intentionally stays on the toggle so keyboard users can keep switching with arrows. */
  setMode(mode: MarkdownEditorMode): void {
    this.mode.set(mode);
  }

  tooltip(action: ToolbarAction): string {
    return action.shortcut ? `${action.label} (${action.shortcut})` : action.label;
  }

  run(action: ToolbarAction): void {
    const textarea = this.textareaRef().nativeElement;
    this.apply(textarea, action.run(this.state(textarea)));
  }

  onKeydown(event: KeyboardEvent): void {
    const textarea = this.textareaRef().nativeElement;
    if (event.target !== textarea || event.isComposing) return;

    if ((event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey) {
      const action = this.shortcuts[event.key.toLowerCase()];
      if (action) {
        event.preventDefault();
        this.run(action);
      }
      return;
    }

    if (event.key === 'Enter' && !event.shiftKey && !event.ctrlKey && !event.metaKey) {
      const edit = continueList(this.state(textarea));
      if (edit) {
        event.preventDefault();
        this.apply(textarea, edit);
      }
    }
  }

  private state(textarea: HTMLTextAreaElement): TextState {
    return { value: textarea.value, start: textarea.selectionStart, end: textarea.selectionEnd };
  }

  /**
   * Replaces text via `execCommand('insertText')` so the change lands in the browser's native
   * undo stack (Ctrl+Z works). `execCommand` is deprecated but still the only way to do that;
   * where it's unavailable we fall back to `setRangeText`, losing only undo. Both paths fire an
   * `input` event, so the form control, validators and dirty state update as if the user typed.
   */
  private apply(textarea: HTMLTextAreaElement, edit: TextEdit): void {
    if (textarea.readOnly || textarea.disabled) return;
    if (edit.from === edit.to && edit.insert === '') return;
    textarea.focus();
    textarea.setSelectionRange(edit.from, edit.to);

    const viaExecCommand =
      edit.insert === ''
        ? this.document.execCommand?.('delete', false)
        : this.document.execCommand?.('insertText', false, edit.insert);
    if (
      !viaExecCommand ||
      textarea.value.slice(edit.from, edit.from + edit.insert.length) !== edit.insert
    ) {
      textarea.setRangeText(edit.insert, edit.from, edit.to);
      textarea.dispatchEvent(new Event('input', { bubbles: true }));
    }
    textarea.setSelectionRange(edit.selectionStart, edit.selectionEnd);
  }
}
