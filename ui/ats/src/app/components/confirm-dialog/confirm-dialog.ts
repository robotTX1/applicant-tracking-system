import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface ConfirmDialogData {
  title?: string;
  message?: string;
  confirmText?: string;
  cancelText?: string;
  confirmColor?: 'primary' | 'accent' | 'warn';
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule],
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.scss',
})
export class ConfirmDialog {
  readonly dialogRef = inject(MatDialogRef<ConfirmDialog>);
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA, { optional: true });

  get title(): string {
    return this.data?.title ?? 'Confirm Action';
  }

  get message(): string {
    return this.data?.message ?? 'Are you sure you want to proceed?';
  }

  get confirmText(): string {
    return this.data?.confirmText ?? 'Confirm';
  }

  get cancelText(): string {
    return this.data?.cancelText ?? 'Cancel';
  }

  get confirmColor(): 'primary' | 'accent' | 'warn' {
    return this.data?.confirmColor ?? 'warn';
  }
}
