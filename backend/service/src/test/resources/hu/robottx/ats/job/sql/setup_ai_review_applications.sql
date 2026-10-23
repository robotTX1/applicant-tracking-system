INSERT INTO job_listings (job_listing_id, company_id, offer_email_template_id, decline_email_template_id,
                          location, title, description, ai_review_enabled, ai_review_min_score, status,
                          created_at, updated_at)
VALUES (8, 1, 1, 2, 'Budapest', 'Manual Tester', 'We are looking for a Manual Tester.', false, 50,
        'PUBLISHED', '2026-01-08 10:00:00', '2026-01-08 10:00:00');

INSERT INTO documents (document_id, original_document_name, mime_type, uploaded_at, object_key, created_at, updated_at)
VALUES (1, 'other_java_cv.pdf', 'application/pdf', '2026-01-10 08:00:00', 'applications/other-user/cv1.pdf',
        '2026-01-10 08:00:00', '2026-01-10 08:00:00'),
       (2, 'third_cloud_cv.pdf', 'application/pdf', '2026-01-10 09:00:00', 'applications/third-user/cv2.pdf',
        '2026-01-10 09:00:00', '2026-01-10 09:00:00'),
       (3, 'other_draft_cv.pdf', 'application/pdf', '2026-01-10 07:00:00', 'applications/other-user/cv3.pdf',
        '2026-01-10 07:00:00', '2026-01-10 07:00:00'),
       (4, 'third_closed_cv.pdf', 'application/pdf', '2026-01-10 07:30:00', 'applications/third-user/cv4.pdf',
        '2026-01-10 07:30:00', '2026-01-10 07:30:00'),
       (5, 'third_reviewed_cv.pdf', 'application/pdf', '2026-01-10 07:45:00', 'applications/third-user/cv5.pdf',
        '2026-01-10 07:45:00', '2026-01-10 07:45:00'),
       (6, 'other_no_ai_cv.pdf', 'application/pdf', '2026-01-10 07:50:00', 'applications/other-user/cv6.pdf',
        '2026-01-10 07:50:00', '2026-01-10 07:50:00');

INSERT INTO applications (application_id, job_listing_id, user_id, cv_document_id, applicant_message,
                          ai_review, ai_score, status, created_at, updated_at)
VALUES (1, 1, 'other-user', 1, 'I have 8 years of Java and Spring Boot experience.', NULL, NULL,
        'APPLIED', '2026-01-10 08:00:00', '2026-01-10 08:00:00'),
       (2, 3, 'third-user', 2, NULL, NULL, NULL,
        'APPLIED', '2026-01-10 09:00:00', '2026-01-10 09:00:00'),
       (3, 2, 'other-user', 3, 'Draft job application', NULL, NULL,
        'APPLIED', '2026-01-10 07:00:00', '2026-01-10 07:00:00'),
       (4, 7, 'third-user', 4, 'Closed job application', NULL, NULL,
        'APPLIED', '2026-01-10 07:30:00', '2026-01-10 07:30:00'),
       (5, 1, 'third-user', 5, 'Already reviewed', 'Previously reviewed candidate', 91,
        'AI_REVIEWED', '2026-01-10 07:45:00', '2026-01-10 07:45:00'),
       (6, 8, 'other-user', 6, 'AI review disabled on published job', NULL, NULL,
        'APPLIED', '2026-01-10 07:50:00', '2026-01-10 07:50:00');

COMMIT;
