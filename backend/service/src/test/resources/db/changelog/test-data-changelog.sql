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

COMMIT;
