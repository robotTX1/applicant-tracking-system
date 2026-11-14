import { JobApplicationResponse, JobApplicationStatus as S } from '../api';
import {
  columnOf,
  compareApplications,
  inTab,
  isDecided,
  moveTargets,
  PIPELINE_COLUMNS,
  PIPELINE_TABS,
  scoreTone,
} from './pipeline';

const app = (id: number, status: S, aiScore?: number, createdAt = `2026-11-0${id}`) =>
  ({ id, status, aiScore, createdAt }) as JobApplicationResponse;

describe('pipeline', () => {
  it('maps every status to exactly one column', () => {
    for (const status of Object.values(S)) {
      expect(PIPELINE_COLUMNS.filter((c) => c.statuses.includes(status))).toHaveLength(1);
    }
    expect(columnOf(S.Applied).id).toBe('new');
    expect(columnOf(S.AiReviewed).id).toBe('new');
    expect(columnOf(S.Declined).id).toBe('declined');
  });

  it('never offers New or the current column as a move target', () => {
    const ids = moveTargets(S.HrInterviewed).map((c) => c.id);
    expect(ids).toEqual(['hr-scheduled', 'tech-scheduled', 'tech-done', 'offer', 'declined']);
    expect(moveTargets(S.Applied).map((c) => c.id)).not.toContain('new');
  });

  it('only allows targets the backend accepts', () => {
    const targets = PIPELINE_COLUMNS.map((c) => c.target).filter(Boolean);
    expect(targets).not.toContain(S.Applied);
    expect(targets).not.toContain(S.AiReviewed);
  });

  it('marks offer and declined as decided and email-sending', () => {
    expect(isDecided(S.Offer)).toBe(true);
    expect(isDecided(S.Declined)).toBe(true);
    expect(isDecided(S.TechnicalInterviewed)).toBe(false);
    expect(PIPELINE_COLUMNS.filter((c) => c.sendsEmail).map((c) => c.id)).toEqual([
      'offer',
      'declined',
    ]);
  });

  it('groups stages into tabs', () => {
    const tab = (id: string) => PIPELINE_TABS.find((t) => t.id === id)!;
    expect(inTab(app(1, S.Applied), tab('new'))).toBe(true);
    expect(inTab(app(1, S.HrInterviewed), tab('hr'))).toBe(true);
    expect(inTab(app(1, S.TechnicalInterviewScheduled), tab('hr'))).toBe(false);
    expect(inTab(app(1, S.TechnicalInterviewScheduled), tab('technical'))).toBe(true);
    expect(inTab(app(1, S.Declined), tab('all'))).toBe(true);
  });

  it('sorts by AI score with unscored applications last in both directions', () => {
    const apps = [app(1, S.Applied), app(2, S.AiReviewed, 40), app(3, S.AiReviewed, 90)];
    const ids = (dir: 'asc' | 'desc') =>
      [...apps].sort(compareApplications('aiScore', dir)).map((a) => a.id);
    expect(ids('desc')).toEqual([3, 2, 1]);
    expect(ids('asc')).toEqual([2, 3, 1]);
  });

  it('sorts by stage order and by applied date, oldest first on ties', () => {
    const apps = [app(3, S.Offer), app(2, S.HrInterviewed), app(1, S.HrInterviewed)];
    expect([...apps].sort(compareApplications('stage', 'asc')).map((a) => a.id)).toEqual([1, 2, 3]);
    expect([...apps].sort(compareApplications('createdAt', 'desc')).map((a) => a.id)).toEqual([
      3, 2, 1,
    ]);
  });

  it('scoreTone buckets scores', () => {
    expect(scoreTone(80)).toBe('high');
    expect(scoreTone(60)).toBe('mid');
    expect(scoreTone(10)).toBe('low');
  });
});
