import { JobListingResponse, JobListingStatus } from '../api';
import { toJobUpdateRequest } from './job-listing';

describe('toJobUpdateRequest', () => {
  const job: JobListingResponse = {
    id: 1,
    companyId: 7,
    company: { id: 7, name: 'Acme', foundingDate: '', websiteUrl: '', description: '' },
    title: 'Dev',
    location: 'Remote',
    description: 'Text',
    offerEmailTemplateId: 3,
    declineEmailTemplateId: 4,
    applicationCount: 2,
    status: JobListingStatus.Draft,
    createdAt: '',
    updatedAt: '',
  };

  it('resends the editable fields with the new status and defaults AI review to off', () => {
    expect(toJobUpdateRequest(job, JobListingStatus.Published)).toEqual({
      title: 'Dev',
      location: 'Remote',
      description: 'Text',
      aiReviewEnabled: false,
      aiReviewMinScore: undefined,
      offerEmailTemplateId: 3,
      declineEmailTemplateId: 4,
      status: JobListingStatus.Published,
    });
  });

  it('keeps the current status by default', () => {
    expect(toJobUpdateRequest(job).status).toBe(JobListingStatus.Draft);
  });
});
