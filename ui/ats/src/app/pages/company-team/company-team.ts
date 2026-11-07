import {
  Component,
  computed,
  inject,
  input,
  numberAttribute,
  resource,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { filter, firstValueFrom, map, switchMap } from 'rxjs';
import { CompanyEmployeeStatus, CompanyRole, EmployeeResponse, EmployeesService } from '../../api';
import {
  AddMemberDialog,
  AddMemberDialogData,
} from '../../components/add-member-dialog/add-member-dialog';
import { ConfirmDialog, ConfirmDialogData } from '../../components/confirm-dialog/confirm-dialog';
import { CompanyRoleService } from '../../services/company-role.service';
import { CompanyStateService } from '../../services/company-state.service';
import { getApiErrorDetails } from '../../utils/api-error';
import { ASSIGNABLE_ROLES, canManageTeam, ROLE_INFO } from '../../utils/company-roles';

/** The API's page size limit; teams are small, so one page holds everyone. */
const TEAM_PAGE_SIZE = 100;

const ROLE_ORDER: Record<CompanyRole, number> = {
  [CompanyRole.Owner]: 0,
  [CompanyRole.CoOwner]: 1,
  [CompanyRole.HrManager]: 2,
  [CompanyRole.TechnicalInterviewer]: 3,
};

/** Owner first, then by role, then by name. */
export function compareMembers(a: EmployeeResponse, b: EmployeeResponse): number {
  return (
    ROLE_ORDER[a.role] - ROLE_ORDER[b.role] ||
    `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`)
  );
}

@Component({
  selector: 'app-company-team',
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
  ],
  templateUrl: './company-team.html',
  styleUrl: './company-team.scss',
})
export class CompanyTeam {
  private readonly employeesService = inject(EmployeesService);
  private readonly companyState = inject(CompanyStateService);
  private readonly companyRoles = inject(CompanyRoleService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  readonly companyId = input.required({ transform: numberAttribute });

  readonly roleInfo = ROLE_INFO;
  readonly assignableRoles = ASSIGNABLE_ROLES;
  readonly Role = CompanyRole;

  readonly company = computed(
    () => this.companyState.companies().find((c) => c.id === this.companyId()) ?? null,
  );

  readonly me = resource({
    params: () => (this.company() ? this.companyId() : undefined),
    loader: ({ params }) => this.companyRoles.getEmployee(params),
  });

  readonly canManage = computed(() => canManageTeam(this.me.value()?.role));

  readonly members = rxResource({
    params: () => (this.company() ? this.companyId() : undefined),
    stream: ({ params }) =>
      this.employeesService
        .getEmployees(params, { pageSize: TEAM_PAGE_SIZE })
        .pipe(map((res) => res.content)),
  });

  readonly showFormer = signal(false);

  private readonly allMembers = computed(() =>
    this.members.hasValue() ? this.members.value() : [],
  );

  readonly activeMembers = computed(() =>
    this.allMembers()
      .filter((m) => m.status === CompanyEmployeeStatus.Active)
      .sort(compareMembers),
  );

  readonly formerMembers = computed(() =>
    this.allMembers()
      .filter((m) => m.status !== CompanyEmployeeStatus.Active)
      .sort(compareMembers),
  );

  /** Id of the member with a role change or removal in flight. */
  readonly busyId = signal<number | null>(null);

  isMe(member: EmployeeResponse): boolean {
    return member.id === this.me.value()?.id;
  }

  /** The owner can't be changed or removed; everyone else can be, by owners and co-owners. */
  canEdit(member: EmployeeResponse): boolean {
    return (
      this.canManage() &&
      member.role !== CompanyRole.Owner &&
      member.status === CompanyEmployeeStatus.Active
    );
  }

  initials(member: EmployeeResponse): string {
    return `${member.firstName.charAt(0)}${member.lastName.charAt(0)}`.toUpperCase() || '?';
  }

  addMember(): void {
    const company = this.company();
    if (!company) return;
    const data: AddMemberDialogData = {
      companyId: company.id,
      companyName: company.name,
      memberUserIds: new Set(this.activeMembers().map((m) => m.userId)),
    };
    this.dialog
      .open<AddMemberDialog, AddMemberDialogData, EmployeeResponse>(AddMemberDialog, {
        data,
        width: '520px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter((added): added is EmployeeResponse => !!added))
      .subscribe((added) => {
        // A former member comes back as the same record, reactivated
        this.members.update((list) => [...(list ?? []).filter((m) => m.id !== added.id), added]);
        this.snackBar.open(
          `${added.firstName} ${added.lastName} joined as ${ROLE_INFO[added.role].label}.`,
          'Close',
          { duration: 4000 },
        );
      });
  }

  changeRole(member: EmployeeResponse, role: CompanyRole): void {
    if (role === member.role) return;
    const selfDemotion = this.isMe(member) && !canManageTeam(role);
    if (!selfDemotion) {
      this.applyRole(member, role).catch(() => undefined);
      return;
    }
    this.confirm({
      title: 'Change your own role?',
      message:
        `As ${ROLE_INFO[role].label} you won't be able to manage the team any more. ` +
        'Another owner or co-owner would have to change it back.',
      confirmText: 'Change my role',
      cancelText: 'Cancel',
      confirmColor: 'warn',
    }).subscribe(() => this.applyRole(member, role).catch(() => undefined));
  }

  removeMember(member: EmployeeResponse): void {
    const name = `${member.firstName} ${member.lastName}`;
    const self = this.isMe(member);
    const companyName = this.company()?.name ?? 'the company';
    this.confirm({
      title: self ? 'Leave this company?' : `Remove ${name}?`,
      message: self
        ? `You'll lose access to ${companyName}'s jobs and candidates. An owner or co-owner can add you again.`
        : `${name} will lose access to ${companyName}'s jobs and candidates. ` +
          'Their feedback stays. You can add them again later.',
      confirmText: self ? 'Leave company' : 'Remove',
      cancelText: 'Cancel',
      confirmColor: 'warn',
    })
      .pipe(
        switchMap(async () => {
          this.busyId.set(member.id);
          try {
            await firstValueFrom(this.employeesService.deleteEmployee(this.companyId(), member.id));
            return true;
          } catch (err: unknown) {
            this.showError(err, `Could not remove ${name}.`);
            return false;
          } finally {
            this.busyId.set(null);
          }
        }),
        filter(Boolean),
      )
      .subscribe(async () => {
        if (self) {
          // Without membership this page (and the company) is no longer ours to see
          this.companyRoles.invalidate(this.companyId());
          await this.companyState.refresh();
          await this.router.navigate(['/companies']);
          this.snackBar.open(`You left ${companyName}.`, 'Close', {
            duration: 4000,
          });
          return;
        }
        this.members.update((list) =>
          list?.map((m) =>
            m.id === member.id ? { ...m, status: CompanyEmployeeStatus.Inactive } : m,
          ),
        );
        this.snackBar.open(`${name} was removed from the team.`, 'Close', { duration: 4000 });
      });
  }

  private async applyRole(member: EmployeeResponse, role: CompanyRole): Promise<void> {
    this.busyId.set(member.id);
    try {
      const updated = await firstValueFrom(
        this.employeesService.updateEmployee(this.companyId(), member.id, { role }),
      );
      this.members.update((list) => list?.map((m) => (m.id === updated.id ? updated : m)));
      if (this.isMe(member)) {
        this.companyRoles.invalidate(this.companyId());
        this.me.reload();
      }
      this.snackBar.open(
        `${member.firstName} ${member.lastName} is now ${ROLE_INFO[role].label}.`,
        'Close',
        { duration: 4000 },
      );
    } catch (err: unknown) {
      this.showError(err, 'Could not change the role.');
    } finally {
      this.busyId.set(null);
    }
  }

  private confirm(data: ConfirmDialogData) {
    return this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(filter((confirmed) => confirmed === true));
  }

  private showError(err: unknown, fallback: string): void {
    this.snackBar.open(getApiErrorDetails(err) ?? fallback, 'Close', { duration: 5000 });
  }
}
