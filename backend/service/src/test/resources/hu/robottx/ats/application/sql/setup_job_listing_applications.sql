INSERT INTO documents (document_id, original_document_name, mime_type, uploaded_at, object_key, created_at, updated_at)
VALUES (1, 'other_java_cv.pdf', 'application/pdf', '2026-01-10 10:00:00', 'applications/other-user/cv1.pdf',
        '2026-01-10 10:00:00', '2026-01-10 10:00:00'),
       (2, 'third_java_cv.pdf', 'application/pdf', '2026-01-11 10:00:00', 'applications/third-user/cv2.pdf',
        '2026-01-11 10:00:00', '2026-01-11 10:00:00'),
       (3, 'third_data_cv.pdf', 'application/pdf', '2026-01-12 10:00:00', 'applications/third-user/cv3.pdf',
        '2026-01-12 10:00:00', '2026-01-12 10:00:00'),
       (4, 'third_devops_cv.pdf', 'application/pdf', '2026-01-13 10:00:00', 'applications/third-user/cv4.pdf',
        '2026-01-13 10:00:00', '2026-01-13 10:00:00');

INSERT INTO applications (application_id, job_listing_id, user_id, cv_document_id, applicant_message,
                          ai_review, ai_score, status, created_at, updated_at)
VALUES (1, 1, 'other-user', 1, 'Interested in Senior Java role', 'Strong Java profile', 88,
        'APPLIED', '2026-01-10 10:00:00', '2026-01-10 10:00:00'),
       (2, 1, 'third-user', 2, 'Ready for technical interview', 'Excellent architecture skills', 95,
        'TECHNICAL_INTERVIEW_SCHEDULED', '2026-01-11 10:00:00', '2026-01-11 10:00:00'),
       (3, 4, 'third-user', 3, 'Applying for Data Engineer', NULL, NULL,
        'HR_INTERVIEWED', '2026-01-12 10:00:00', '2026-01-12 10:00:00'),
       (4, 5, 'third-user', 4, 'DevOps candidate', 'Good CI/CD', 80,
        'TECHNICAL_INTERVIEWED', '2026-01-13 10:00:00', '2026-01-13 10:00:00');

COMMIT;
