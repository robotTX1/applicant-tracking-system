import { Component, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { CompanyResponse } from '../../api';
import { CompanyStateService } from '../../services/company-state.service';
import { ResourceLimit } from '../../enums/resource-limit.enum';
import {
  DeleteCompanyDialog,
  DeleteCompanyDialogData,
} from '../../components/delete-company-dialog/delete-company-dialog';
import { ExternalUrlPipe } from '../../pipes/external-url.pipe';

@Component({
  selector: 'app-company-list',
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatDividerModule,
    ExternalUrlPipe,
  ],
  templateUrl: './company-list.html',
  styleUrl: './company-list.scss',
})
export class CompanyList {
  protected readonly companyState = inject(CompanyStateService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly maxCompanies = ResourceLimit.MAX_COMPANIES;
  readonly companies = this.companyState.companies;
  readonly ownedCount = this.companyState.ownedCompaniesCount;
  readonly canCreateCompany = this.companyState.canCreateCompany;

  constructor() {
    if (
      typeof this.companyState.init === 'function' &&
      (!this.companyState.companies || this.companyState.companies().length === 0)
    ) {
      this.companyState.init().catch((err: unknown) => {
        console.error('Failed to initialize company state in company list:', err);
      });
    }
  }

  getCompanyInitial(name: string): string {
    return name?.trim()?.charAt(0)?.toUpperCase() || 'C';
  }

  /** The dialog makes the user type the name and performs the delete; it closes with true. */
  deleteCompany(company: CompanyResponse): void {
    this.dialog
      .open<DeleteCompanyDialog, DeleteCompanyDialogData, boolean>(DeleteCompanyDialog, {
        data: { company },
        width: '520px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((deleted) => {
        if (!deleted) return;
        this.companyState.notifyCompanyDeleted(company.id);
        this.snackBar.open(`Company "${company.name}" was deleted.`, 'Close', {
          duration: 4000,
        });
      });
  }
}
