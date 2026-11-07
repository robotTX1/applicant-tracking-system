import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, Subject, throwError } from 'rxjs';
import {
  ApplicationFeedbackType as T,
  ApplicationsService,
  CompanyEmployeeStatus,
  CompanyRole,
  EmployeeResponse,
  FeedbackResponse,
  FeedbacksService,
  JobApplicationResponse,
  JobApplicationStatus as S,
  JobListingResponse,
  JobListingStatus,
} from '../../api';
import { PIPELINE_COLUMNS } from '../../utils/pipeline';
import { ApplicationPanel, ApplicationPanelData } from './application-panel';

const employee = (id: number, role: CompanyRole, firstName: string): EmployeeResponse => ({
  id,
  companyId: 7,
  userId: `u${id}`,
  firstName,
  lastName: 'Doe',
  email: '',
  role,
  status: CompanyEmployeeStatus.Active,
  assignedDate: '',
});

const feedback = (id: number, employeeId: number, type: T, score = 80): FeedbackResponse => ({
  id,
  applicationId: 9,
  employeeId,
  type,
  feedback: `Feedback ${id}`,
  score,
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
});

const application = (status: S): JobApplicationResponse => ({
  id: 9,
  jobListingId: 5,
  userId: 'cand',
  applicantFirstName: 'Ann',
  applicantLastName: 'Smith',
  applicantEmail: 'ann@example.com',
  cvDocumentId: 1,
  applicantMessage: 'Hello there',
  aiScore: 82,
  aiReview: 'Strong **Java** skills',
  status,
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '',
});

describe('ApplicationPanel', () => {
  let fixture: ComponentFixture<ApplicationPanel>;
  let component: ApplicationPanel;
  let feedbacksService: {
    getApplicationFeedbacks: ReturnType<typeof vi.fn>;
    saveApplicationFeedback: ReturnType<typeof vi.fn>;
    updateApplicationFeedback: ReturnType<typeof vi.fn>;
    deleteApplicationFeedback: ReturnType<typeof vi.fn>;
  };
  let move: ReturnType<typeof vi.fn<ApplicationPanelData['move']>>;
  let dialogResult: Subject<boolean>;
  let snackBar: { open: ReturnType<typeof vi.fn> };

  const me = employee(1, CompanyRole.HrManager, 'Me');
  const colleague = employee(2, CompanyRole.TechnicalInterviewer, 'Tom');

  async function setup(
    status: S,
    feedbacks: FeedbackResponse[] = [],
    overrides: Partial<ApplicationPanelData> = {},
  ) {
    feedbacksService = {
      getApplicationFeedbacks: vi.fn().mockReturnValue(
        of({
          content: feedbacks,
          page: { size: 100, number: 0, totalElements: feedbacks.length, totalPages: 1 },
        }),
      ),
      saveApplicationFeedback: vi
        .fn()
        .mockImplementation((_id: number, body: object) =>
          of({ ...feedback(50, 1, T.Hr), ...body }),
        ),
      updateApplicationFeedback: vi
        .fn()
        .mockImplementation((_a: number, id: number, body: object) =>
          of({ ...feedbacks.find((f) => f.id === id)!, ...body }),
        ),
      deleteApplicationFeedback: vi.fn().mockReturnValue(of(undefined)),
    };
    move = vi.fn<ApplicationPanelData['move']>();
    dialogResult = new Subject();
    snackBar = { open: vi.fn() };

    const data: ApplicationPanelData = {
      application: application(status),
      job: {
        id: 5,
        companyId: 7,
        aiReviewEnabled: true,
        status: JobListingStatus.Published,
      } as JobListingResponse,
      me,
      employees: new Map([
        [1, me],
        [2, colleague],
      ]),
      canManage: true,
      move,
      ...overrides,
    };

    await TestBed.configureTestingModule({
      imports: [ApplicationPanel],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: FeedbacksService, useValue: feedbacksService },
        { provide: ApplicationsService, useValue: {} },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => dialogResult }) } },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ApplicationPanel);
    component = fixture.componentInstance;
    await fixture.whenStable();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const button = (text: string) =>
    [...el().querySelectorAll<HTMLButtonElement>('button')].find((b) =>
      b.textContent?.includes(text),
    );

  it('shows the applicant, message, AI review and stage', async () => {
    await setup(S.AiReviewed);
    expect(el().textContent).toContain('Ann Smith');
    expect(el().querySelector('.applicant-email')?.getAttribute('href')).toBe(
      'mailto:ann@example.com',
    );
    expect(el().textContent).toContain('Hello there');
    expect(el().querySelector('.ai-score')?.textContent).toContain('82 / 100');
    expect(el().querySelector('.ai-review strong')?.textContent).toBe('Java');
    expect(el().querySelector('.stage-chip')?.textContent).toContain('New');
  });

  it('lists feedback with author names and loads it newest last', async () => {
    await setup(S.TechnicalInterviewed, [feedback(1, 1, T.Hr), feedback(2, 2, T.Technical, 40)]);
    expect(feedbacksService.getApplicationFeedbacks).toHaveBeenCalledWith(9, {
      sort: 'createdAt,asc',
      pageSize: 100,
    });
    const items = el().querySelectorAll('.feedback-item');
    expect(items[0].textContent).toContain('HR interview');
    expect(items[0].textContent).toContain('You');
    expect(items[1].textContent).toContain('Tom Doe');
    expect(items[1].querySelector('.feedback-score')?.classList).toContain('tone-low');
  });

  it('lets an HR manager add HR feedback in the HR interviewed stage', async () => {
    await setup(S.HrInterviewed);
    expect(component.addableType()).toBe(T.Hr);
    button('Add HR interview feedback')!.click();
    await fixture.whenStable();

    component.feedbackForm.setValue({ score: 75, feedback: '  Great communicator ' });
    await component.saveFeedback();
    await fixture.whenStable();

    expect(feedbacksService.saveApplicationFeedback).toHaveBeenCalledWith(9, {
      score: 75,
      feedback: 'Great communicator',
    });
    expect(el().querySelector('.feedback-item')?.textContent).toContain('Great communicator');
    expect(component.addableType()).toBeNull(); // one per person per type
  });

  it('validates score range and required text', async () => {
    await setup(S.HrInterviewed);
    component.startAdding();
    component.feedbackForm.setValue({ score: 101, feedback: '   ' });
    await component.saveFeedback();
    expect(feedbacksService.saveApplicationFeedback).not.toHaveBeenCalled();
    expect(component.feedbackForm.controls.score.hasError('max')).toBe(true);
    expect(component.feedbackForm.controls.feedback.hasError('blank')).toBe(true);
  });

  it('does not let an HR manager give technical feedback', async () => {
    await setup(S.TechnicalInterviewed);
    expect(component.addableType()).toBeNull();
    expect(button('Add')).toBeUndefined();
  });

  it('explains when feedback can be given outside the interviewed stages', async () => {
    await setup(S.HrInterviewScheduled);
    expect(el().querySelector('.feedback-hint')?.textContent).toContain('HR interviewed');
  });

  it('lets the author edit and delete their feedback only while in its stage', async () => {
    await setup(S.HrInterviewed, [feedback(1, 1, T.Hr), feedback(2, 2, T.Technical)]);
    const [mine, theirs] = component.feedbacks.value()!;
    expect(component.canModify(mine)).toBe(true);
    expect(component.canModify(theirs)).toBe(false);

    component.startEditing(mine);
    component.feedbackForm.controls.feedback.setValue('Updated');
    await component.saveFeedback();
    expect(feedbacksService.updateApplicationFeedback).toHaveBeenCalledWith(9, 1, {
      score: 80,
      feedback: 'Updated',
    });

    component.deleteFeedback(mine);
    dialogResult.next(true);
    await fixture.whenStable();
    expect(feedbacksService.deleteApplicationFeedback).toHaveBeenCalledWith(9, 1);
    expect(component.feedbacks.value()!.map((f) => f.id)).toEqual([2]);
  });

  it('locks feedback once the application has moved on', async () => {
    await setup(S.TechnicalInterviewScheduled, [feedback(1, 1, T.Hr)]);
    expect(component.canModify(component.feedbacks.value()![0])).toBe(false);
  });

  it('reports a failed save and keeps the form open', async () => {
    await setup(S.HrInterviewed);
    feedbacksService.saveApplicationFeedback.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { details: 'Already given' } })),
    );
    component.startAdding();
    component.feedbackForm.setValue({ score: 50, feedback: 'Ok' });
    await component.saveFeedback();
    expect(snackBar.open).toHaveBeenCalledWith('Already given', 'Close', expect.anything());
    expect(component.editingId()).toBe('new');
  });

  it('moves through the board and reflects the new stage', async () => {
    await setup(S.HrInterviewScheduled);
    const target = PIPELINE_COLUMNS.find((c) => c.id === 'hr-done')!;
    move.mockResolvedValue({ ...application(S.HrInterviewed) });
    await component.moveTo(target);
    await fixture.whenStable();
    expect(move).toHaveBeenCalledWith(
      expect.objectContaining({ id: 9 }),
      target,
      expect.any(Function),
    );
    expect(el().querySelector('.stage-chip')?.textContent).toContain('HR interviewed');
    expect(component.addableType()).toBe(T.Hr);
  });

  it('hides moving for read-only users', async () => {
    await setup(S.TechnicalInterviewed, [], { canManage: false });
    expect(el().querySelector('.move-button')).toBeNull();
  });
});
