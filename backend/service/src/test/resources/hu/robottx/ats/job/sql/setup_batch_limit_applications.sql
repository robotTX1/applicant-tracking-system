INSERT INTO documents (document_id, original_document_name, mime_type, uploaded_at, object_key, created_at, updated_at)
VALUES (1, 'cv1.pdf', 'application/pdf', '2026-01-10 10:00:00', 'applications/other-user/cv1.pdf',
        '2026-01-10 10:00:00', '2026-01-10 10:00:00'),
       (2, 'cv2.pdf', 'application/pdf', '2026-01-10 08:00:00', 'applications/third-user/cv2.pdf',
        '2026-01-10 08:00:00', '2026-01-10 08:00:00'),
       (3, 'cv3.pdf', 'application/pdf', '2026-01-10 09:00:00', 'applications/other-user/cv3.pdf',
        '2026-01-10 09:00:00', '2026-01-10 09:00:00'),
       (4, 'cv4.pdf', 'application/pdf', '2026-01-10 09:00:00', 'applications/third-user/cv4.pdf',
        '2026-01-10 09:00:00', '2026-01-10 09:00:00');

INSERT INTO applications (application_id, job_listing_id, user_id, cv_document_id, applicant_message,
                          ai_review, ai_score, status, created_at, updated_at)
VALUES (1, 1, 'other-user', 1, 'Fourth oldest candidate', NULL, NULL,
        'APPLIED', '2026-01-10 10:00:00', '2026-01-10 10:00:00'),
       (2, 1, 'third-user', 2, 'Oldest candidate', NULL, NULL,
        'APPLIED', '2026-01-10 08:00:00', '2026-01-10 08:00:00'),
       (3, 3, 'other-user', 3, 'Second oldest candidate', NULL, NULL,
        'APPLIED', '2026-01-10 09:00:00', '2026-01-10 09:00:00'),
       (4, 5, 'third-user', 4, 'Third oldest candidate', NULL, NULL,
        'APPLIED', '2026-01-10 09:00:00', '2026-01-10 09:00:00');

COMMIT;
