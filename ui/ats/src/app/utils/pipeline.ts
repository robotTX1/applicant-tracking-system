import { JobApplicationResponse, JobApplicationStatus as S } from '../api';

export type PipelineColumnId =
  'new' | 'hr-scheduled' | 'hr-done' | 'tech-scheduled' | 'tech-done' | 'offer' | 'declined';

export interface PipelineColumn {
  id: PipelineColumnId;
  title: string;
  /** Application statuses shown in this column. */
  statuses: readonly S[];
  /** Status an application gets when moved here; null = nothing can be moved here. */
  target: S | null;
  /** Shown to technical interviewers (the backend only returns these statuses to them). */
  technical: boolean;
  /** Moving here emails the candidate (offer / decline), so it needs a confirmation. */
  sendsEmail: boolean;
}

export const PIPELINE_COLUMNS: readonly PipelineColumn[] = [
  {
    id: 'new',
    title: 'New',
    statuses: [S.Applied, S.AiReviewed],
    target: null,
    technical: false,
    sendsEmail: false,
  },
  {
    id: 'hr-scheduled',
    title: 'HR interview scheduled',
    statuses: [S.HrInterviewScheduled],
    target: S.HrInterviewScheduled,
    technical: false,
    sendsEmail: false,
  },
  {
    id: 'hr-done',
    title: 'HR interviewed',
    statuses: [S.HrInterviewed],
    target: S.HrInterviewed,
    technical: false,
    sendsEmail: false,
  },
  {
    id: 'tech-scheduled',
    title: 'Tech interview scheduled',
    statuses: [S.TechnicalInterviewScheduled],
    target: S.TechnicalInterviewScheduled,
    technical: true,
    sendsEmail: false,
  },
  {
    id: 'tech-done',
    title: 'Tech interviewed',
    statuses: [S.TechnicalInterviewed],
    target: S.TechnicalInterviewed,
    technical: true,
    sendsEmail: false,
  },
  {
    id: 'offer',
    title: 'Offer',
    statuses: [S.Offer],
    target: S.Offer,
    technical: false,
    sendsEmail: true,
  },
  {
    id: 'declined',
    title: 'Declined',
    statuses: [S.Declined],
    target: S.Declined,
    technical: false,
    sendsEmail: true,
  },
];

const COLUMN_BY_STATUS = new Map<S, PipelineColumn>(
  PIPELINE_COLUMNS.flatMap((column) => column.statuses.map((s) => [s, column] as const)),
);

export function columnOf(status: S): PipelineColumn {
  return COLUMN_BY_STATUS.get(status)!;
}

/** Offer and Declined are final: the candidate was emailed, so the card is locked. */
export function isDecided(status: S): boolean {
  return status === S.Offer || status === S.Declined;
}

/** Columns an application can be moved to from its current status. */
export function moveTargets(status: S): PipelineColumn[] {
  const current = columnOf(status);
  return PIPELINE_COLUMNS.filter((c) => c.target !== null && c !== current);
}

export function applicantName(application: JobApplicationResponse): string {
  return `${application.applicantFirstName} ${application.applicantLastName}`.trim();
}

export type PipelineTabId = 'all' | 'new' | 'hr' | 'technical' | 'offer' | 'declined';

export interface PipelineTab {
  id: PipelineTabId;
  label: string;
  /** Stages included; null = every stage. */
  stages: readonly PipelineColumnId[] | null;
}

export const PIPELINE_TABS: readonly PipelineTab[] = [
  { id: 'all', label: 'All', stages: null },
  { id: 'new', label: 'New', stages: ['new'] },
  { id: 'hr', label: 'HR', stages: ['hr-scheduled', 'hr-done'] },
  { id: 'technical', label: 'Technical', stages: ['tech-scheduled', 'tech-done'] },
  { id: 'offer', label: 'Offer', stages: ['offer'] },
  { id: 'declined', label: 'Declined', stages: ['declined'] },
];

export function inTab(application: JobApplicationResponse, tab: PipelineTab): boolean {
  return !tab.stages || tab.stages.includes(columnOf(application.status).id);
}

export type ApplicationSortKey = 'name' | 'aiScore' | 'stage' | 'createdAt';
export type SortDirection = 'asc' | 'desc';

const STAGE_ORDER = new Map(PIPELINE_COLUMNS.map((c, i) => [c.id, i]));

/**
 * Table order. Unscored applications sort below scored ones in either direction; ties fall back
 * to the longest-waiting candidate first.
 */
export function compareApplications(
  key: ApplicationSortKey,
  direction: SortDirection,
): (a: JobApplicationResponse, b: JobApplicationResponse) => number {
  const sign = direction === 'asc' ? 1 : -1;
  const byCreated = (a: JobApplicationResponse, b: JobApplicationResponse) =>
    a.createdAt.localeCompare(b.createdAt);
  return (a, b) => {
    let result = 0;
    switch (key) {
      case 'name':
        result = sign * applicantName(a).localeCompare(applicantName(b));
        break;
      case 'aiScore': {
        const as = a.aiScore ?? null;
        const bs = b.aiScore ?? null;
        if (as === null || bs === null) result = as === bs ? 0 : as === null ? 1 : -1;
        else result = sign * (as - bs);
        break;
      }
      case 'stage':
        result =
          sign *
          (STAGE_ORDER.get(columnOf(a.status).id)! - STAGE_ORDER.get(columnOf(b.status).id)!);
        break;
      case 'createdAt':
        result = sign * byCreated(a, b);
        break;
    }
    return result || byCreated(a, b);
  };
}

/** Tone for an AI score badge. */
export function scoreTone(score: number): 'high' | 'mid' | 'low' {
  return score >= 75 ? 'high' : score >= 50 ? 'mid' : 'low';
}
