import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { CompanyEmailTemplateType } from '../../api';

export interface TemplatePreviewDialogData {
  type: CompanyEmailTemplateType | string;
  subject: string;
  body: string;
}

@Component({
  selector: 'app-template-preview-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './template-preview-dialog.html',
  styleUrl: './template-preview-dialog.scss',
})
export class TemplatePreviewDialog {
  readonly dialogRef = inject(MatDialogRef<TemplatePreviewDialog>);
  readonly data = inject<TemplatePreviewDialogData>(MAT_DIALOG_DATA, { optional: true });

  get isOffer(): boolean {
    return this.data?.type === CompanyEmailTemplateType.Offer;
  }

  get typeLabel(): string {
    return this.isOffer ? 'Offer Template' : 'Decline Template';
  }

  get subject(): string {
    return this.data?.subject ?? '';
  }

  get body(): string {
    return this.data?.body ?? '';
  }
}
