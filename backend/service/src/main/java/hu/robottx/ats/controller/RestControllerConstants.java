package hu.robottx.ats.controller;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RestControllerConstants {

    public static final String USERS_ENDPOINT = "/users";
    public static final String CURRENT_USER_ENDPOINT = USERS_ENDPOINT + "/me";
    public static final String CURRENT_USER_CV_ENDPOINT = CURRENT_USER_ENDPOINT + "/cv";
    public static final String CURRENT_USER_CV_METADATA_ENDPOINT = CURRENT_USER_CV_ENDPOINT + "/metadata";
    public static final String CURRENT_USER_APPLICATIONS_ENDPOINT = CURRENT_USER_ENDPOINT + "/applications";
    public static final String CURRENT_USER_APPLICATION_BY_ID_ENDPOINT =
            CURRENT_USER_APPLICATIONS_ENDPOINT + "/{applicationId}";
    public static final String CURRENT_USER_APPLICATION_CV_ENDPOINT =
            CURRENT_USER_APPLICATION_BY_ID_ENDPOINT + "/cv";
    public static final String CURRENT_USER_APPLICATION_CV_METADATA_ENDPOINT =
            CURRENT_USER_APPLICATION_CV_ENDPOINT + "/metadata";
    public static final String COMPANIES_ENDPOINT = "/companies";
    public static final String COMPANY_BY_ID_ENDPOINT = COMPANIES_ENDPOINT + "/{id}";
    public static final String EMPLOYEES_ENDPOINT = COMPANIES_ENDPOINT + "/{companyId}/employees";
    public static final String EMPLOYEE_BY_ID_ENDPOINT = EMPLOYEES_ENDPOINT + "/{employeeId}";
    public static final String JOB_LISTINGS_ENDPOINT = "/job-listings";
    public static final String JOB_LISTING_BY_ID_ENDPOINT = JOB_LISTINGS_ENDPOINT + "/{jobListingId}";
    public static final String JOB_LISTING_APPLICATIONS_ENDPOINT = JOB_LISTING_BY_ID_ENDPOINT + "/applications";
    public static final String JOB_LISTING_APPLICATION_BY_ID_ENDPOINT =
            JOB_LISTING_APPLICATIONS_ENDPOINT + "/{applicationId}";
    public static final String JOB_LISTING_APPLICATION_CV_ENDPOINT =
            JOB_LISTING_APPLICATION_BY_ID_ENDPOINT + "/cv";
    public static final String JOB_LISTING_APPLICATION_CV_METADATA_ENDPOINT =
            JOB_LISTING_APPLICATION_CV_ENDPOINT + "/metadata";
    public static final String EMAIL_TEMPLATES_ENDPOINT = COMPANIES_ENDPOINT + "/{companyId}/email-templates";
    public static final String EMAIL_TEMPLATE_BY_ID_ENDPOINT = EMAIL_TEMPLATES_ENDPOINT + "/{emailTemplateId}";

}
