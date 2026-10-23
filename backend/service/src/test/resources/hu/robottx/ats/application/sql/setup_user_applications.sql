INSERT INTO job_listings (job_listing_id, company_id, location, title, description, ai_review_enabled,
                          ai_review_min_score, status, created_at, updated_at)
VALUES (8, 4, 'Budapest', 'Backend Engineer', 'We are looking for a Backend Engineer.', true, 75,
        'PUBLISHED', '2026-01-08 10:00:00', '2026-01-08 10:00:00');

INSERT INTO documents (document_id, original_document_name, mime_type, uploaded_at, object_key, created_at, updated_at)
VALUES (1, 'test_cv_1.pdf', 'application/pdf', '2026-01-10 10:00:00', 'applications/test-user/cv1.pdf',
        '2026-01-10 10:00:00', '2026-01-10 10:00:00'),
       (2, 'test_cv_2.pdf', 'application/pdf', '2026-01-11 10:00:00', 'applications/test-user/cv2.pdf',
        '2026-01-11 10:00:00', '2026-01-11 10:00:00'),
       (3, 'other_cv.pdf', 'application/pdf', '2026-01-12 10:00:00', 'applications/other-user/cv3.pdf',
        '2026-01-12 10:00:00', '2026-01-12 10:00:00');

INSERT INTO applications (application_id, job_listing_id, user_id, cv_document_id, applicant_message,
                          ai_review, ai_score, status, created_at, updated_at)
VALUES (1, 5, 'test-user', 1, 'Application to DevOps role', 'Good DevOps experience', 85,
        'APPLIED', '2026-01-10 10:00:00', '2026-01-10 10:00:00'),
       (2, 8, 'test-user', 2, 'Application to Backend role', 'Strong Java skills', 92,
        'HR_INTERVIEW_SCHEDULED', '2026-01-11 10:00:00', '2026-01-11 10:00:00'),
       (3, 1, 'other-user', 3, 'Other user application', 'Solid background', 78,
        'APPLIED', '2026-01-12 10:00:00', '2026-01-12 10:00:00');

COMMIT;
