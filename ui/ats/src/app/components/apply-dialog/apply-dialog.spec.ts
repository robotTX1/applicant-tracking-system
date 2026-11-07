import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';
import {
  ApplicationsService,
  DocumentResponse,
  JobApplicationStatus,
  JobListingStatus,
  UserApplicationResponse,
  UsersService,
} from '../../api';
import { ApplyDialog, ApplyDialogData } from './apply-dialog';

const pdf = (name = 'cv.pdf', size = 1024) =>
  new File([new Uint8Array(size)], name, { type: 'application/pdf' });

describe('ApplyDialog', () => {
  const data: ApplyDialogData = {
    job: {
      id: 7,
      companyId: 3,
      company: { id: 3, name: 'Acme', foundingDate: '', websiteUrl: '', description: '' },
      title: 'Senior Java Developer',
      location: 'Budapest',
      description: '',
      status: JobListingStatus.Published,
      createdAt: '',
      updatedAt: '',
    },
    companyName: 'Acme',
  };
  const profileCv: DocumentResponse = {
    id: 5,
    originalDocumentName: 'my-cv.pdf',
    mimeType: 'application/pdf',
    uploadedAt: '2026-10-01T10:00:00Z',
  };
  const application: UserApplicationResponse = {
    id: 1,
    jobListingId: 7,
    userId: 'u',
    cvDocumentId: 9,
    status: JobApplicationStatus.Applied,
    createdAt: '',
    updatedAt: '',
  };

  let applicationsService: { saveCurrentUserApplication: ReturnType<typeof vi.fn> };
  let usersService: {
    getCurrentUserCvMetadata: ReturnType<typeof vi.fn>;
    uploadCurrentUserCv: ReturnType<typeof vi.fn>;
  };
  let dialogRef: { close: ReturnType<typeof vi.fn>; disableClose: boolean };

  async function create(cv: 'present' | 'missing' | 'error' = 'present') {
    usersService.getCurrentUserCvMetadata.mockReturnValue(
      cv === 'present'
        ? of(profileCv)
        : throwError(() => new HttpErrorResponse({ status: cv === 'missing' ? 404 : 500 })),
    );
    const fixture = TestBed.createComponent(ApplyDialog);
    await fixture.whenStable();
    return {
      fixture,
      component: fixture.componentInstance,
      el: fixture.nativeElement as HTMLElement,
    };
  }

  const submitButton = (el: HTMLElement) =>
    [...el.querySelectorAll<HTMLButtonElement>('mat-dialog-actions button')].find((b) =>
      b.textContent?.includes('Submit'),
    )!;
  const selectFile = (component: ApplyDialog, file: File) =>
    component.onFileSelected({
      files: [file],
      value: 'C:\\fakepath',
    } as unknown as HTMLInputElement);

  beforeEach(() => {
    applicationsService = { saveCurrentUserApplication: vi.fn().mockReturnValue(of(application)) };
    usersService = {
      getCurrentUserCvMetadata: vi.fn(),
      uploadCurrentUserCv: vi.fn().mockReturnValue(of(profileCv)),
    };
    dialogRef = { close: vi.fn(), disableClose: false };

    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: ApplicationsService, useValue: applicationsService },
        { provide: UsersService, useValue: usersService },
      ],
    });
  });

  it('shows the job, company and profile CV, defaulting to the profile CV', async () => {
    const { el, component } = await create();
    expect(el.querySelector('[mat-dialog-title]')?.textContent).toContain('Senior Java Developer');
    expect(el.textContent).toContain('Acme');
    expect(el.textContent).toContain('my-cv.pdf');
    expect(component.cvSource()).toBe('profile');
    expect(submitButton(el).disabled).toBe(false);
  });

  it('applies with the profile CV and a trimmed message', async () => {
    const { component } = await create();
    component.messageModel.set({ message: '  Hire me!  ' });
    await component.submit();

    expect(applicationsService.saveCurrentUserApplication).toHaveBeenCalledWith(
      { jobListingId: 7, applicantMessage: 'Hire me!' },
      undefined,
    );
    expect(usersService.uploadCurrentUserCv).not.toHaveBeenCalled();
    expect(dialogRef.close).toHaveBeenCalledWith({
      status: 'applied',
      application,
      profileCvSaveFailed: false,
    });
  });

  it('omits an empty message', async () => {
    const { component } = await create();
    component.messageModel.set({ message: '   ' });
    await component.submit();
    expect(applicationsService.saveCurrentUserApplication.mock.lastCall![0]).toEqual({
      jobListingId: 7,
      applicantMessage: undefined,
    });
  });

  it('requires a file when uploading a different CV, without touching the profile CV', async () => {
    const { fixture, component, el } = await create();
    component.cvSource.set('upload');
    await fixture.whenStable();
    expect(submitButton(el).disabled).toBe(true);
    expect(el.querySelector('mat-checkbox')).toBeNull();

    const file = pdf();
    selectFile(component, file);
    await fixture.whenStable();
    expect(el.querySelector('.file-name')?.textContent).toBe('cv.pdf');
    expect(submitButton(el).disabled).toBe(false);

    await component.submit();
    expect(applicationsService.saveCurrentUserApplication.mock.lastCall![1]).toBe(file);
    expect(usersService.uploadCurrentUserCv).not.toHaveBeenCalled();
  });

  it('rejects invalid files with a visible error', async () => {
    const { fixture, component, el } = await create('missing');
    selectFile(component, new File(['x'], 'cv.png', { type: 'image/png' }));
    await fixture.whenStable();
    expect(el.querySelector('.file-error')?.textContent).toContain('Only PDF');
    expect(component.file()).toBeNull();
    expect(submitButton(el).disabled).toBe(true);
  });

  describe('without a profile CV', () => {
    it('asks for a file and offers to save it to the profile (on by default)', async () => {
      const { component, el } = await create('missing');
      expect(component.cvSource()).toBe('upload');
      expect(el.textContent).toContain("You don't have a CV on your profile yet");
      expect(el.querySelector('mat-radio-group')).toBeNull();
      expect(el.querySelector('mat-checkbox')?.textContent).toContain('save as my profile CV');
      expect(submitButton(el).disabled).toBe(true);
    });

    it('applies with the file and then saves it as the profile CV', async () => {
      const { component } = await create('missing');
      const file = pdf();
      selectFile(component, file);
      await component.submit();

      expect(applicationsService.saveCurrentUserApplication.mock.lastCall![1]).toBe(file);
      expect(usersService.uploadCurrentUserCv).toHaveBeenCalledWith(file);
      expect(dialogRef.close).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'applied', profileCvSaveFailed: false }),
      );
    });

    it('skips saving to the profile when unchecked', async () => {
      const { component } = await create('missing');
      selectFile(component, pdf());
      component.saveAsProfileCv.set(false);
      await component.submit();
      expect(usersService.uploadCurrentUserCv).not.toHaveBeenCalled();
    });

    it('still reports success when saving the profile CV fails', async () => {
      usersService.uploadCurrentUserCv.mockReturnValue(throwError(() => new Error('500')));
      const { component } = await create('missing');
      selectFile(component, pdf());
      await component.submit();
      expect(dialogRef.close).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'applied', profileCvSaveFailed: true }),
      );
    });
  });

  it('falls back to uploading when the profile CV check fails', async () => {
    const { component, el } = await create('error');
    expect(component.cvSource()).toBe('upload');
    expect(el.textContent).toContain("couldn't check your profile CV");
  });

  it('blocks closing while submitting and re-enables it on error', async () => {
    const response = new Subject<UserApplicationResponse>();
    applicationsService.saveCurrentUserApplication.mockReturnValue(response);
    const { fixture, component, el } = await create();

    const pending = component.submit();
    await fixture.whenStable();
    expect(dialogRef.disableClose).toBe(true);
    expect(submitButton(el).textContent).toContain('Submitting');
    expect(submitButton(el).disabled).toBe(true);

    response.error(new HttpErrorResponse({ status: 500 }));
    await pending;
    expect(dialogRef.disableClose).toBe(false);
    expect(component.submitting()).toBe(false);
  });

  it('closes as already-applied on 409', async () => {
    applicationsService.saveCurrentUserApplication.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );
    const { component } = await create();
    await component.submit();
    expect(dialogRef.close).toHaveBeenCalledWith({ status: 'already-applied' });
  });

  it('shows the backend message for 400 and keeps the dialog open', async () => {
    applicationsService.saveCurrentUserApplication.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { details: 'Employees cannot apply to job listings of their own company' },
          }),
      ),
    );
    const { fixture, component, el } = await create();
    await component.submit();
    await fixture.whenStable();
    expect(el.querySelector('.submit-error')?.textContent).toContain('Employees cannot apply');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('explains when the job closed in the meantime (404)', async () => {
    applicationsService.saveCurrentUserApplication.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    const { fixture, component, el } = await create();
    await component.submit();
    await fixture.whenStable();
    expect(el.querySelector('.submit-error')?.textContent).toContain('no longer accepting');
  });

  it('disables submit when the message is too long', async () => {
    const { fixture, component, el } = await create();
    component.messageModel.set({ message: 'x'.repeat(2001) });
    await fixture.whenStable();
    expect(submitButton(el).disabled).toBe(true);
  });
});
