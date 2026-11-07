import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { ConfirmDialog } from '../components/confirm-dialog/confirm-dialog';
import { map, Observable } from 'rxjs';

export interface HasPendingChanges {
  hasPendingChanges: () => boolean;
}

export const pendingChangesGuard: CanDeactivateFn<HasPendingChanges> = (
  component,
): boolean | Observable<boolean> => {
  if (!component || !component.hasPendingChanges || !component.hasPendingChanges()) {
    return true;
  }

  const dialog = inject(MatDialog);
  const dialogRef = dialog.open(ConfirmDialog, {
    width: '420px',
    data: {
      title: 'Discard Unsaved Changes?',
      message:
        'You have unsaved changes in your company registration form. Are you sure you want to leave without saving?',
      confirmText: 'Discard & Leave',
      cancelText: 'Stay on Page',
      confirmColor: 'warn',
    },
  });

  return dialogRef.afterClosed().pipe(map((result) => Boolean(result)));
};
