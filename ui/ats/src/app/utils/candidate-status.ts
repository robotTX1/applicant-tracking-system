import { JobApplicationStatus } from '../api';

/** Stages shown to candidates. Internal steps (AI review, scheduled vs. done) are merged. */
export const CANDIDATE_STAGES = [
  'Submitted',
  'Under review',
  'HR interview',
  'Technical interview',
  'Offer',
] as const;

export type CandidateTone = 'active' | 'offer' | 'declined';

export interface CandidateStatus {
  label: string;
  /** Index in {@link CANDIDATE_STAGES}, or null when the process stopped (declined). */
  step: number | null;
  tone: CandidateTone;
  /** A decision has been made (offer or declined). */
  decided: boolean;
  /** Withdrawing is offered until the candidate is declined. */
  canWithdraw: boolean;
}

const S = JobApplicationStatus;

const STEP_BY_STATUS: Record<JobApplicationStatus, number | null> = {
  [S.Applied]: 0,
  [S.AiReviewed]: 1,
  [S.HrInterviewScheduled]: 2,
  [S.HrInterviewed]: 2,
  [S.TechnicalInterviewScheduled]: 3,
  [S.TechnicalInterviewed]: 3,
  [S.Offer]: 4,
  [S.Declined]: null,
};

export function toCandidateStatus(status: JobApplicationStatus): CandidateStatus {
  if (status === S.Declined) {
    return {
      label: 'Not selected',
      step: null,
      tone: 'declined',
      decided: true,
      canWithdraw: false,
    };
  }
  const step = STEP_BY_STATUS[status] ?? 0;
  const isOffer = status === S.Offer;
  return {
    label: CANDIDATE_STAGES[step],
    step,
    tone: isOffer ? 'offer' : 'active',
    decided: isOffer,
    canWithdraw: true,
  };
}

/** Backend statuses for the "In progress" / "Decided" filters. */
export const IN_PROGRESS_STATUSES: JobApplicationStatus[] = [
  S.Applied,
  S.AiReviewed,
  S.HrInterviewScheduled,
  S.HrInterviewed,
  S.TechnicalInterviewScheduled,
  S.TechnicalInterviewed,
];
export const DECIDED_STATUSES: JobApplicationStatus[] = [S.Offer, S.Declined];
