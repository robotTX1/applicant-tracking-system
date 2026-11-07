import { JobListingResponse, JobListingStatus, JobListingUpdateRequest } from '../api';

/** The update endpoint replaces the whole listing, so a status change resends everything else. */
export function toJobUpdateRequest(
  job: JobListingResponse,
  status: JobListingStatus = job.status,
): JobListingUpdateRequest {
  return {
    title: job.title,
    location: job.location,
    description: job.description,
    aiReviewEnabled: job.aiReviewEnabled ?? false,
    aiReviewMinScore: job.aiReviewMinScore,
    offerEmailTemplateId: job.offerEmailTemplateId!,
    declineEmailTemplateId: job.declineEmailTemplateId!,
    status,
  };
}

export const JOB_STATUS_LABELS: Record<JobListingStatus, string> = {
  [JobListingStatus.Draft]: 'Draft',
  [JobListingStatus.Published]: 'Published',
  [JobListingStatus.Closed]: 'Closed',
};
