import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import Keycloak from 'keycloak-js';
import { of, Subject, throwError } from 'rxjs';
import { DocumentResponse, UsersService } from '../../api';
import { Profile } from './profile';

const pdf = (name = 'new-cv.pdf', size = 1024) =>
  new File([new Uint8Array(size)], name, { type: 'application/pdf' });

describe('Profile', () => {
  const user = { firstName: 'Teszt', lastName: 'Elek', email: 'test@test.com', phoneNumber: '' };
  const cv: DocumentResponse = {
    id: 5,
    originalDocumentName: 'my-cv.pdf',
    mimeType: 'application/pdf',
    uploadedAt: '2026-11-01T15:09:48Z',
  };

  let usersService: {
    getCurrentUser: ReturnType<typeof vi.fn>;
    getCurrentUserCvMetadata: ReturnType<typeof vi.fn>;
    uploadCurrentUserCv: ReturnType<typeof vi.fn>;
    getCurrentUserCv: ReturnType<typeof vi.fn>;
    deleteCurrentUserCv: ReturnType<typeof vi.fn>;
  };
  let dialogResult: Subject<boolean | undefined>;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let snackBar: { open: ReturnType<typeof vi.fn> };
  let keycloak: { createAccountUrl: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    usersService = {
      getCurrentUser: vi.fn().mockReturnValue(of(user)),
      getCurrentUserCvMetadata: vi.fn().mockReturnValue(of(cv)),
      uploadCurrentUserCv: vi
        .fn()
        .mockReturnValue(of({ ...cv, id: 6, originalDocumentName: 'new-cv.pdf' })),
      getCurrentUserCv: vi.fn().mockReturnValue(of(new Blob(['%PDF']))),
      deleteCurrentUserCv: vi.fn().mockReturnValue(of(undefined)),
    };
    dialogResult = new Subject();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => dialogResult }) };
    snackBar = { open: vi.fn() };
    keycloak = {
      createAccountUrl: vi.fn().mockReturnValue('https://auth.example/realms/ats/account'),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: UsersService, useValue: usersService },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: Keycloak, useValue: keycloak },
      ],
    });
  });

  async function create() {
    const fixture = TestBed.createComponent(Profile);
    await fixture.whenStable();
    return {
      fixture,
      component: fixture.componentInstance,
      el: fixture.nativeElement as HTMLElement,
    };
  }

  const button = (el: HTMLElement, text: string) =>
    [...el.querySelectorAll<HTMLButtonElement>('button')].find((b) =>
      b.textContent?.includes(text),
    );
  const choose = (component: Profile, file: File) =>
    component.onFileSelected({ files: [file], value: 'x' } as unknown as HTMLInputElement);

  it('shows the personal details, initials and account link', async () => {
    const { el } = await create();
    expect(el.querySelector('.avatar')?.textContent?.trim()).toBe('TE');
    expect(el.querySelector('.details')?.textContent).toContain('Teszt Elek');
    expect(el.querySelector('.details')?.textContent).toContain('test@test.com');
    expect(el.querySelector('dd.empty')?.textContent).toContain('Not provided');
    const account = el.querySelector<HTMLAnchorElement>('a[target="_blank"]')!;
    expect(account.getAttribute('href')).toBe('https://auth.example/realms/ats/account');
    expect(account.rel).toBe('noopener noreferrer');
  });

  it('hides the account link when Keycloak cannot build it', async () => {
    keycloak.createAccountUrl.mockImplementation(() => {
      throw new Error('not initialised');
    });
    const { el } = await create();
    expect(el.querySelector('a[target="_blank"]')).toBeNull();
  });

  it('shows a retry when the details fail to load', async () => {
    usersService.getCurrentUser.mockReturnValue(throwError(() => new Error('500')));
    const { fixture, el } = await create();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain("Couldn't load your details");
    usersService.getCurrentUser.mockReturnValue(of(user));
    button(el, 'Try again')!.click();
    await fixture.whenStable();
    expect(el.querySelector('.details')).not.toBeNull();
  });

  describe('with a CV', () => {
    it('shows the file with download, replace and delete', async () => {
      const { el } = await create();
      expect(el.querySelector('.file-name')?.textContent).toBe('my-cv.pdf');
      expect(button(el, 'Download')).toBeDefined();
      expect(button(el, 'Replace')).toBeDefined();
      expect(button(el, 'Delete')).toBeDefined();
    });

    it('replaces the CV and shows the new file', async () => {
      const { fixture, component, el } = await create();
      const file = pdf();
      await choose(component, file);
      await fixture.whenStable();
      expect(usersService.uploadCurrentUserCv).toHaveBeenCalledWith(file);
      expect(el.querySelector('.file-name')?.textContent).toBe('new-cv.pdf');
      expect(snackBar.open).toHaveBeenCalledWith('Your CV was replaced.', 'Close', {
        duration: 4000,
      });
    });

    it('rejects invalid files before uploading', async () => {
      const { fixture, component, el } = await create();
      await choose(component, new File(['x'], 'cv.docx', { type: 'application/msword' }));
      await fixture.whenStable();
      expect(usersService.uploadCurrentUserCv).not.toHaveBeenCalled();
      expect(el.querySelector('.file-error')?.textContent).toContain('Only PDF');
    });

    it('shows the backend error when the upload fails', async () => {
      usersService.uploadCurrentUserCv.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 400,
              error: { details: 'File type not allowed. Supported types: PDF' },
            }),
        ),
      );
      const { fixture, component, el } = await create();
      await choose(component, pdf());
      await fixture.whenStable();
      expect(el.querySelector('.file-error')?.textContent).toContain('File type not allowed');
      expect(el.querySelector('.file-name')?.textContent).toBe('my-cv.pdf');
      expect(component.busy()).toBeNull();
    });

    it('downloads the CV under its original name', async () => {
      Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:cv'), revokeObjectURL: vi.fn() });
      let name: string | undefined;
      const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
        this: HTMLAnchorElement,
      ) {
        name = this.download;
      });
      const { fixture, el } = await create();
      button(el, 'Download')!.click();
      await fixture.whenStable();
      expect(usersService.getCurrentUserCv).toHaveBeenCalled();
      expect(name).toBe('my-cv.pdf');
      click.mockRestore();
    });

    it('asks before deleting and explains that applications keep their copy', async () => {
      const { el } = await create();
      button(el, 'Delete')!.click();
      expect(dialog.open.mock.lastCall![1].data.message).toContain('keep their own copy');
      dialogResult.next(false);
      expect(usersService.deleteCurrentUserCv).not.toHaveBeenCalled();
    });

    it('deletes after confirmation and shows the upload prompt', async () => {
      const { fixture, el } = await create();
      button(el, 'Delete')!.click();
      dialogResult.next(true);
      await fixture.whenStable();
      expect(usersService.deleteCurrentUserCv).toHaveBeenCalled();
      expect(el.querySelector('.no-cv')).not.toBeNull();
      expect(snackBar.open).toHaveBeenCalledWith('Your CV was deleted.', 'Close', {
        duration: 4000,
      });
    });

    it('keeps the CV when deleting fails', async () => {
      usersService.deleteCurrentUserCv.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );
      const { fixture, el } = await create();
      button(el, 'Delete')!.click();
      dialogResult.next(true);
      await fixture.whenStable();
      expect(el.querySelector('.file-name')).not.toBeNull();
      expect(snackBar.open).toHaveBeenCalledWith('Could not delete the CV.', 'Close', {
        duration: 5000,
      });
    });
  });

  describe('without a CV', () => {
    beforeEach(() => {
      usersService.getCurrentUserCvMetadata.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 404 })),
      );
    });

    it('prompts to upload one', async () => {
      const { el } = await create();
      expect(el.querySelector('.no-cv')?.textContent).toContain("haven't uploaded a CV");
      expect(button(el, 'Upload CV')).toBeDefined();
      expect(button(el, 'Delete')).toBeUndefined();
    });

    it('uploads and then shows the file', async () => {
      const { fixture, component, el } = await create();
      await choose(component, pdf());
      await fixture.whenStable();
      expect(el.querySelector('.file-name')?.textContent).toBe('new-cv.pdf');
      expect(snackBar.open).toHaveBeenCalledWith('Your CV was uploaded.', 'Close', {
        duration: 4000,
      });
    });
  });

  it('shows a retry when the CV check fails for other reasons', async () => {
    usersService.getCurrentUserCvMetadata.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    const { el } = await create();
    expect(el.textContent).toContain("Couldn't load your CV");
  });

  it('disables the CV buttons while an action is running', async () => {
    const upload = new Subject<DocumentResponse>();
    usersService.uploadCurrentUserCv.mockReturnValue(upload);
    const { fixture, component, el } = await create();
    const pending = choose(component, pdf());
    await fixture.whenStable();
    expect(button(el, 'Download')!.disabled).toBe(true);
    expect(button(el, 'Delete')!.disabled).toBe(true);
    upload.next(cv);
    upload.complete();
    await pending;
    await fixture.whenStable();
    expect(button(el, 'Download')!.disabled).toBe(false);
  });
});
