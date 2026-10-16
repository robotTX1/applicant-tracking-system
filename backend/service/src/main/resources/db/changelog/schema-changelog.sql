--liquibase formatted sql

--changeset Csaba Csíkos:create-documents-table
--Creates Documents table
--rollback DROP TABLE documents
CREATE TABLE documents
(
    document_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_document_name VARCHAR(255) NOT NULL,
    mime_type              VARCHAR(255) NOT NULL,
    uploaded_at            DATETIME(6)  NOT NULL,
    object_key             VARCHAR(255) NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL
);

--changeset Csaba Csíkos:create-companies-table
--Creates Companies table
--rollback DROP TABLE companies
CREATE TABLE companies
(
    company_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL UNIQUE,
    founding_date DATE          NOT NULL,
    website_url   VARCHAR(2000) NOT NULL,
    description   VARCHAR(2000) NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL
);

--changeset Csaba Csíkos:create-users-table
--Creates Users table
--rollback DROP TABLE users
CREATE TABLE users
(
    user_id        VARCHAR(36) PRIMARY KEY NOT NULL,
    first_name     VARCHAR(60)  NOT NULL,
    last_name      VARCHAR(60)  NOT NULL,
    email          VARCHAR(255) NOT NULL,
    phone_number   VARCHAR(15)  NOT NULL,
    cv_document_id BIGINT,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    CONSTRAINT fk_documents_document_id_users FOREIGN KEY (cv_document_id)
        REFERENCES documents (document_id) ON DELETE SET NULL ON UPDATE CASCADE
);

--changeset Csaba Csíkos:create-employees-table
--Creates Employees table
--rollback DROP TABLE employees
CREATE TABLE employees
(
    employee_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       VARCHAR(36) NOT NULL,
    company_id    BIGINT      NOT NULL,
    role          VARCHAR(50) NOT NULL,
    status        VARCHAR(50) NOT NULL,
    assigned_date DATETIME(6) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    CONSTRAINT fk_employees_user_id_users FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_employees_company_id_companies FOREIGN KEY (company_id)
        REFERENCES companies (company_id) ON DELETE CASCADE ON UPDATE CASCADE
);

--changeset Csaba Csíkos:create-job_listings-table
--Creates Job Listings table
--rollback DROP TABLE job_listings
CREATE TABLE job_listings
(
    job_listing_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT        NOT NULL,
    location            VARCHAR(255)  NOT NULL,
    title               VARCHAR(255)  NOT NULL,
    description         VARCHAR(4000) NOT NULL,
    ai_review_enabled   BOOLEAN       NOT NULL,
    ai_review_min_score INT           NOT NULL,
    status              VARCHAR(50)   NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    CONSTRAINT fk_companies_company_id_job_listings FOREIGN KEY (company_id)
        REFERENCES companies (company_id) ON DELETE CASCADE ON UPDATE CASCADE
);

--changeset Csaba Csíkos:create-applications-table
--Creates Applications table
--rollback DROP TABLE applications
CREATE TABLE applications
(
    application_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_listing_id    BIGINT      NOT NULL,
    user_id           VARCHAR(36) NOT NULL,
    cv_document_id    BIGINT      NOT NULL,
    applicant_message VARCHAR(2000),
    ai_review         VARCHAR(2000),
    ai_score          INT,
    status            VARCHAR(50) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    CONSTRAINT fk_applications_job_listing_id_job_listings FOREIGN KEY (job_listing_id)
        REFERENCES job_listings (job_listing_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_applications_user_id_users FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_applications_cv_document_id_documents FOREIGN KEY (cv_document_id)
        REFERENCES documents (document_id) ON DELETE CASCADE ON UPDATE CASCADE
);

--changeset Csaba Csíkos:create-feedbacks-table
--Creates Feedbacks table
--rollback DROP TABLE feedbacks
CREATE TABLE feedbacks
(
    feedback_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id    BIGINT        NOT NULL,
    application_id BIGINT,
    type           VARCHAR(50)   NOT NULL,
    feedback       VARCHAR(2000) NOT NULL,
    score          INT           NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    CONSTRAINT fk_feedbacks_employee_id_employees FOREIGN KEY (employee_id)
        REFERENCES employees (employee_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_feedbacks_application_id_applications FOREIGN KEY (application_id)
        REFERENCES applications (application_id) ON DELETE CASCADE ON UPDATE CASCADE
);

--changeset Csaba Csíkos:create-email_templates-table
--Creates Email Templates table
--rollback DROP TABLE email_templates
CREATE TABLE email_templates
(
    email_template_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT        NOT NULL,
    type              VARCHAR(50)   NOT NULL,
    subject           VARCHAR(255)  NOT NULL,
    body              VARCHAR(2000) NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    CONSTRAINT fk_email_templates_company_id_companies FOREIGN KEY (company_id)
        REFERENCES companies (company_id) ON DELETE CASCADE ON UPDATE CASCADE
);