import { JobApplicationStatus as S } from '../api';
import {
  CANDIDATE_STAGES,
  DECIDED_STATUSES,
  IN_PROGRESS_STATUSES,
  toCandidateStatus,
} from './candidate-status';

describe('toCandidateStatus', () => {
  it.each([
    [S.Applied, 'Submitted', 0],
    [S.AiReviewed, 'Under review', 1],
    [S.HrInterviewScheduled, 'HR interview', 2],
    [S.HrInterviewed, 'HR interview', 2],
    [S.TechnicalInterviewScheduled, 'Technical interview', 3],
    [S.TechnicalInterviewed, 'Technical interview', 3],
  ])('%s shows as "%s" (step %d), in progress', (status, label, step) => {
    expect(toCandidateStatus(status)).toEqual({
      label,
      step,
      tone: 'active',
      decided: false,
      canWithdraw: true,
    });
  });

  it('never exposes the AI review to candidates', () => {
    expect(toCandidateStatus(S.AiReviewed).label).not.toMatch(/ai/i);
  });

  it('marks an offer as decided but still withdrawable', () => {
    expect(toCandidateStatus(S.Offer)).toEqual({
      label: 'Offer',
      step: 4,
      tone: 'offer',
      decided: true,
      canWithdraw: true,
    });
  });

  it('shows declined as "Not selected" without a step or withdraw option', () => {
    expect(toCandidateStatus(S.Declined)).toEqual({
      label: 'Not selected',
      step: null,
      tone: 'declined',
      decided: true,
      canWithdraw: false,
    });
  });

  it('covers every backend status exactly once across the filters', () => {
    const all = Object.values(S);
    expect([...IN_PROGRESS_STATUSES, ...DECIDED_STATUSES].sort()).toEqual([...all].sort());
  });

  it('has a label for each progress step', () => {
    expect(CANDIDATE_STAGES).toHaveLength(5);
  });
});
