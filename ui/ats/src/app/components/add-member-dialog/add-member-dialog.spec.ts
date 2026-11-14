import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { CompanyRole, EmployeesService, UserResponse, UsersService } from '../../api';
import { AddMemberDialog, AddMemberDialogData } from './add-member-dialog';

describe('AddMemberDialog', () => {
  let fixture: ComponentFixture<AddMemberDialog>;
  let component: AddMemberDialog;
  let usersService: { lookupUser: ReturnType<typeof vi.fn> };
  let employeesService: { saveEmployee: ReturnType<typeof vi.fn> };
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  const nina: UserResponse = {
    id: 'kc-nina',
    firstName: 'Nina',
    lastName: 'Doe',
    email: 'nina@example.com',
  };

  beforeEach(async () => {
    usersService = { lookupUser: vi.fn().mockReturnValue(of(nina)) };
    employeesService = {
      saveEmployee: vi.fn().mockReturnValue(of({ id: 9, userId: 'kc-nina', role: 'HR_MANAGER' })),
    };
    dialogRef = { close: vi.fn() };
    const data: AddMemberDialogData = {
      companyId: 7,
      companyName: 'Acme',
      memberUserIds: new Set(['kc-existing']),
    };
    await TestBed.configureTestingModule({
      imports: [AddMemberDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: UsersService, useValue: usersService },
        { provide: EmployeesService, useValue: employeesService },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AddMemberDialog);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  const el = () => fixture.nativeElement as HTMLElement;

  async function find(email: string) {
    component.form.controls.email.setValue(email);
    await component.findUser();
    await fixture.whenStable();
  }

  it('looks up the account by exact email', async () => {
    await find(' nina@example.com ');
    expect(usersService.lookupUser).toHaveBeenCalledWith('nina@example.com');
    expect(el().querySelector('.user-card')?.textContent).toContain('Nina Doe');
    expect(el().querySelectorAll('mat-radio-button').length).toBe(3); // no Owner option
  });

  it('does not search with an invalid email', async () => {
    await find('not-an-email');
    expect(usersService.lookupUser).not.toHaveBeenCalled();
  });

  it('explains when no account uses the email', async () => {
    usersService.lookupUser.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    await find('nobody@example.com');
    expect(el().textContent).toContain('No account uses nobody@example.com');
    expect(component.lookup().kind).toBe('not-found');
  });

  it('catches people who are already in the team', async () => {
    usersService.lookupUser.mockReturnValue(of({ ...nina, id: 'kc-existing' }));
    await find('nina@example.com');
    expect(el().textContent).toContain('already in the team');
  });

  it('adds the member with the chosen role and closes with the result', async () => {
    await find('nina@example.com');
    component.form.controls.role.setValue(CompanyRole.TechnicalInterviewer);
    await component.add();
    expect(employeesService.saveEmployee).toHaveBeenCalledWith(7, {
      userId: 'kc-nina',
      role: CompanyRole.TechnicalInterviewer,
    });
    expect(dialogRef.close).toHaveBeenCalledWith(expect.objectContaining({ id: 9 }));
  });

  it('shows a friendly message on 409', async () => {
    employeesService.saveEmployee.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );
    await find('nina@example.com');
    await component.add();
    expect(component.saveError()).toBe('Nina is already a member of Acme.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('reports other lookup failures, e.g. no permission', async () => {
    usersService.lookupUser.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 403,
            error: { details: 'You do not have the necessary permissions' },
          }),
      ),
    );
    await find('nina@example.com');
    expect(component.lookup()).toEqual({
      kind: 'error',
      message: 'You do not have the necessary permissions',
    });
  });

  it('resets the lookup when the email changes', async () => {
    await find('nina@example.com');
    component.form.controls.email.setValue('other@example.com');
    expect(component.lookup().kind).toBe('idle');
  });
});
