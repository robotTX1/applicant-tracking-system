UPDATE employees
SET role = 'TECHNICAL_INTERVIEWER'
WHERE employee_id = 2;

INSERT INTO employees (employee_id, user_id, company_id, role, status, assigned_date, created_at, updated_at)
VALUES (6, 'other-user', 1, 'HR_MANAGER', 'ACTIVE', '2026-01-01 10:00:00', '2026-01-01 10:00:00', '2026-01-01 10:00:00'),
       (7, 'third-user', 1, 'TECHNICAL_INTERVIEWER', 'ACTIVE', '2026-01-01 10:00:00', '2026-01-01 10:00:00', '2026-01-01 10:00:00'),
       (8, 'test-user', 4, 'CO_OWNER', 'ACTIVE', '2026-01-04 10:00:00', '2026-01-04 10:00:00', '2026-01-04 10:00:00'),
       (9, 'other-user', 2, 'OWNER', 'ACTIVE', '2026-01-02 10:00:00', '2026-01-02 10:00:00', '2026-01-02 10:00:00');

INSERT INTO job_listings (job_listing_id, company_id, offer_email_template_id, decline_email_template_id,
                          location, title, description, ai_review_enabled, ai_review_min_score, status,
                          created_at, updated_at)
VALUES (8, 2, 3, 7, 'Budapest', 'Platform Engineer', 'We are looking for a Platform Engineer.', false, 60,
        'PUBLISHED', '2026-01-03 11:00:00', '2026-01-03 11:00:00'),
       (9, 3, 4, 5, 'Szeged', 'ML Engineer', 'We are looking for an ML Engineer.', false, 60,
        'PUBLISHED', '2026-01-04 11:00:00', '2026-01-04 11:00:00');

INSERT INTO documents (document_id, original_document_name, mime_type, uploaded_at, object_key, created_at, updated_at)
VALUES (101, 'cv_1.pdf', 'application/pdf', '2026-01-08 10:00:00', 'cv/third-user/cv_1.pdf',
        '2026-01-08 10:00:00', '2026-01-08 10:00:00'),
       (102, 'cv_2.pdf', 'application/pdf', '2026-01-08 11:00:00', 'cv/other-user/cv_2.pdf',
        '2026-01-08 11:00:00', '2026-01-08 11:00:00'),
       (103, 'cv_3.pdf', 'application/pdf', '2026-01-08 12:00:00', 'cv/third-user/cv_3.pdf',
        '2026-01-08 12:00:00', '2026-01-08 12:00:00'),
       (104, 'cv_4.pdf', 'application/pdf', '2026-01-08 13:00:00', 'cv/other-user/cv_4.pdf',
        '2026-01-08 13:00:00', '2026-01-08 13:00:00'),
       (105, 'cv_5.pdf', 'application/pdf', '2026-01-08 14:00:00', 'cv/other-user/cv_5.pdf',
        '2026-01-08 14:00:00', '2026-01-08 14:00:00'),
       (106, 'cv_6.pdf', 'application/pdf', '2026-01-08 15:00:00', 'cv/third-user/cv_6.pdf',
        '2026-01-08 15:00:00', '2026-01-08 15:00:00'),
       (107, 'cv_7.pdf', 'application/pdf', '2026-01-08 16:00:00', 'cv/other-user/cv_7.pdf',
        '2026-01-08 16:00:00', '2026-01-08 16:00:00'),
       (108, 'cv_8.pdf', 'application/pdf', '2026-01-08 17:00:00', 'cv/third-user/cv_8.pdf',
        '2026-01-08 17:00:00', '2026-01-08 17:00:00'),
       (109, 'cv_9.pdf', 'application/pdf', '2026-01-08 18:00:00', 'cv/third-user/cv_9.pdf',
        '2026-01-08 18:00:00', '2026-01-08 18:00:00'),
       (110, 'cv_10.pdf', 'application/pdf', '2026-01-08 19:00:00', 'cv/other-user/cv_10.pdf',
        '2026-01-08 19:00:00', '2026-01-08 19:00:00'),
       (111, 'cv_11.pdf', 'application/pdf', '2026-01-08 20:00:00', 'cv/third-user/cv_11.pdf',
        '2026-01-08 20:00:00', '2026-01-08 20:00:00');

INSERT INTO applications (application_id, job_listing_id, user_id, cv_document_id, applicant_message,
                          ai_review, ai_score, status, created_at, updated_at)
VALUES (1, 1, 'third-user', 101, 'Application 1 in HR_INTERVIEWED (Company 1)',
        'Good fit', 85, 'HR_INTERVIEWED', '2026-01-08 10:00:00', '2026-01-09 10:00:00'),
       (2, 1, 'other-user', 102, 'Application 2 in TECHNICAL_INTERVIEWED (Company 1)',
        'Strong candidate', 90, 'TECHNICAL_INTERVIEWED', '2026-01-08 11:00:00', '2026-01-09 11:00:00'),
       (3, 3, 'third-user', 103, 'Application 3 in TECHNICAL_INTERVIEWED (Company 2)',
        'Cloud skills verified', 82, 'TECHNICAL_INTERVIEWED', '2026-01-08 12:00:00', '2026-01-09 12:00:00'),
       (4, 2, 'other-user', 104, 'Application 4 in APPLIED (Company 1)',
        NULL, NULL, 'APPLIED', '2026-01-08 13:00:00', '2026-01-09 13:00:00'),
       (5, 3, 'other-user', 105, 'Application 5 in TECHNICAL_INTERVIEW_SCHEDULED (Company 2)',
        'Cloud review passed', 84, 'TECHNICAL_INTERVIEW_SCHEDULED', '2026-01-08 14:00:00', '2026-01-09 14:00:00'),
       (6, 4, 'third-user', 106, 'Application 6 in HR_INTERVIEWED (Company 3)',
        NULL, NULL, 'HR_INTERVIEWED', '2026-01-08 15:00:00', '2026-01-09 15:00:00'),
       (7, 4, 'other-user', 107, 'Application 7 in TECHNICAL_INTERVIEWED (Company 3)',
        NULL, NULL, 'TECHNICAL_INTERVIEWED', '2026-01-08 16:00:00', '2026-01-09 16:00:00'),
       (8, 8, 'third-user', 108, 'Application 8 in HR_INTERVIEWED (Company 2)',
        'Good profile', 81, 'HR_INTERVIEWED', '2026-01-08 17:00:00', '2026-01-09 17:00:00'),
       (9, 5, 'third-user', 109, 'Application 9 in OFFER (Company 4)',
        'Excellent DevOps profile', 92, 'OFFER', '2026-01-08 18:00:00', '2026-01-09 18:00:00'),
       (10, 5, 'other-user', 110, 'Application 10 in TECHNICAL_INTERVIEWED (Company 4)',
        'Solid background', 80, 'TECHNICAL_INTERVIEWED', '2026-01-08 19:00:00', '2026-01-09 19:00:00'),
       (11, 9, 'third-user', 111, 'Application 11 in HR_INTERVIEWED (Company 3)',
        NULL, NULL, 'HR_INTERVIEWED', '2026-01-08 20:00:00', '2026-01-09 20:00:00');

INSERT INTO feedbacks (feedback_id, application_id, employee_id, type, feedback, score, created_at, updated_at)
VALUES (1, 1, 6, 'HR', 'Strong communication skills during HR interview.', 85,
        '2026-01-10 10:00:00', '2026-01-10 10:00:00'),
       (2, 2, 6, 'HR', 'Passed HR screening with flying colors.', 90,
        '2026-01-11 10:00:00', '2026-01-11 10:00:00'),
       (3, 2, 1, 'TECHNICAL', 'Solid Java and system design knowledge.', 88,
        '2026-01-12 10:00:00', '2026-01-12 10:00:00'),
       (4, 2, 7, 'TECHNICAL', 'Good coding exercise solution.', 80,
        '2026-01-13 10:00:00', '2026-01-13 10:00:00'),
       (5, 3, 9, 'HR', 'Positive HR interview for Cloud Architect.', 91,
        '2026-01-14 10:00:00', '2026-01-14 10:00:00'),
       (6, 3, 9, 'TECHNICAL', 'Strong AWS and Kubernetes architecture skills.', 89,
        '2026-01-14 11:00:00', '2026-01-14 11:00:00'),
       (7, 6, 3, 'HR', 'Good cultural fit for DataFlow.', 86,
        '2026-01-15 10:00:00', '2026-01-15 10:00:00'),
       (8, 7, 3, 'HR', 'Initial HR screening passed.', 84,
        '2026-01-15 11:00:00', '2026-01-15 11:00:00'),
       (9, 7, 4, 'TECHNICAL', 'Strong Spark and Kafka knowledge.', 92,
        '2026-01-15 12:00:00', '2026-01-15 12:00:00'),
       (10, 8, 9, 'HR', 'HR feedback in Company 2.', 83,
        '2026-01-16 10:00:00', '2026-01-16 10:00:00'),
       (11, 9, 8, 'HR', 'Great culture fit.', 95,
        '2026-01-16 11:00:00', '2026-01-16 11:00:00');

ALTER TABLE feedbacks AUTO_INCREMENT = 51;

COMMIT;
