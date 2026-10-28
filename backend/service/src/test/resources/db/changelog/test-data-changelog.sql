-- liquibase formatted sql

-- Test Users
-- changeset Csaba Csíkos:load-test-users
-- Loads test users
INSERT INTO users (user_id, first_name, last_name, email, phone_number, created_at, updated_at)
VALUES ('test-user', 'Test', 'User', 'test.user@example.com', '+36301234567',
        '2026-01-01 09:00:00', '2026-01-01 09:00:00'),
       ('other-user', 'Other', 'User', 'other.user@example.com', '+36307654321',
        '2026-01-01 09:00:00', '2026-01-01 09:00:00'),
       ('third-user', 'Third', 'User', 'third.user@example.com', '+36309876543',
        '2026-01-01 09:00:00', '2026-01-01 09:00:00');

-- Test Companies
-- changeset Csaba Csíkos:load-test-companies
-- Loads test companies
INSERT INTO companies (company_id, name, founding_date, website_url, description, created_at, updated_at)
VALUES (1, 'TechCorp', '2015-03-10', 'https://techcorp.example.com',
        'Enterprise software solutions provider.', '2026-01-01 10:00:00', '2026-01-01 10:00:00'),
       (2, 'InnovateLabs', '2020-07-22', 'https://innovatelabs.example.com',
        'AI and cloud research company.', '2026-01-02 10:00:00', '2026-01-02 10:00:00'),
       (3, 'DataFlow Systems', '2018-11-05', 'https://dataflow.example.com',
        'Big data analytics and streaming platform.', '2026-01-03 10:00:00', '2026-01-03 10:00:00'),
       (4, 'CloudNine', '2022-01-15', 'https://cloudnine.example.com',
        'Modern cloud infrastructure services.', '2026-01-04 10:00:00', '2026-01-04 10:00:00');

-- Test Employees
-- changeset Csaba Csíkos:load-test-employees
-- Loads test employees
INSERT INTO employees (employee_id, user_id, company_id, role, status, assigned_date, created_at, updated_at)
VALUES (1, 'test-user', 1, 'OWNER', 'ACTIVE', '2026-01-01 10:00:00', '2026-01-01 10:00:00', '2026-01-01 10:00:00'),
       (2, 'test-user', 2, 'OWNER', 'ACTIVE', '2026-01-02 10:00:00', '2026-01-02 10:00:00', '2026-01-02 10:00:00'),
       (3, 'test-user', 3, 'HR_MANAGER', 'ACTIVE', '2026-01-03 10:00:00', '2026-01-03 10:00:00', '2026-01-03 10:00:00'),
       (4, 'other-user', 3, 'OWNER', 'ACTIVE', '2026-01-03 10:00:00', '2026-01-03 10:00:00', '2026-01-03 10:00:00'),
       (5, 'other-user', 4, 'OWNER', 'ACTIVE', '2026-01-04 10:00:00', '2026-01-04 10:00:00', '2026-01-04 10:00:00');

-- Test Email Templates
-- changeset Csaba Csíkos:load-test-email-templates
-- Loads test email templates
INSERT INTO email_templates (email_template_id, company_id, type, subject, body, created_at, updated_at)
VALUES (1, 1, 'OFFER', 'Software Engineer Offer',
        'Congratulations! We are pleased to offer you the Software Engineer position.',
        '2026-01-01 10:00:00', '2026-01-01 10:00:00'),
       (2, 1, 'DECLINE', 'Application Update',
        'Thank you for your interest. Unfortunately, we will not be moving forward.',
        '2026-01-02 10:00:00', '2026-01-02 10:00:00'),
       (3, 2, 'OFFER', 'Welcome to InnovateLabs',
        'We are excited to extend this offer to you.',
        '2026-01-03 10:00:00', '2026-01-03 10:00:00'),
       (4, 3, 'OFFER', 'Offer from DataFlow',
        'We are pleased to offer you the role at DataFlow.',
        '2026-01-04 10:00:00', '2026-01-04 10:00:00'),
       (5, 3, 'DECLINE', 'DataFlow Application Status',
        'Thank you for interviewing with DataFlow.',
        '2026-01-05 10:00:00', '2026-01-05 10:00:00'),
       (6, 4, 'OFFER', 'CloudNine Offer',
        'Welcome to CloudNine.',
        '2026-01-06 10:00:00', '2026-01-06 10:00:00'),
       (7, 2, 'DECLINE', 'InnovateLabs Application Status',
        'Thank you for applying to InnovateLabs.',
        '2026-01-03 10:00:00', '2026-01-03 10:00:00'),
       (8, 4, 'DECLINE', 'CloudNine Application Status',
        'Thank you for applying to CloudNine.',
        '2026-01-06 10:00:00', '2026-01-06 10:00:00');

-- Test Job Listings
-- changeset Csaba Csíkos:load-test-job-listings
-- Loads test job listings
INSERT INTO job_listings (job_listing_id, company_id, offer_email_template_id, decline_email_template_id,
                          location, title, description, ai_review_enabled, ai_review_min_score, status,
                          created_at, updated_at)
VALUES (1, 1, 1, 2, 'Budapest', 'Senior Java Developer', 'We are looking for a Senior Java Developer.', true, 70,
        'PUBLISHED', '2026-01-01 10:00:00', '2026-01-01 10:00:00'),
       (2, 1, 1, 2, 'Debrecen', 'Junior Frontend Developer', 'We are looking for a Junior Frontend Developer.', false, 50,
        'DRAFT', '2026-01-02 10:00:00', '2026-01-02 10:00:00'),
       (3, 2, 3, 7, 'Budapest', 'Cloud Architect', 'We are looking for a Cloud Architect.', true, 80,
        'PUBLISHED', '2026-01-03 10:00:00', '2026-01-03 10:00:00'),
       (4, 3, 4, 5, 'Szeged', 'Data Engineer', 'We are looking for a Data Engineer.', false, 60,
        'CLOSED', '2026-01-04 10:00:00', '2026-01-04 10:00:00'),
       (5, 4, 6, 8, 'Budapest', 'DevOps Engineer', 'We are looking for a DevOps Engineer.', true, 75,
        'PUBLISHED', '2026-01-05 10:00:00', '2026-01-05 10:00:00'),
       (6, 4, 6, 8, 'Remote', 'Product Manager', 'We are looking for a Product Manager.', false, 65,
        'DRAFT', '2026-01-06 10:00:00', '2026-01-06 10:00:00'),
       (7, 4, 6, 8, 'Budapest', 'QA Engineer', 'We are looking for a QA Engineer.', true, 70,
        'CLOSED', '2026-01-07 10:00:00', '2026-01-07 10:00:00');

COMMIT;
