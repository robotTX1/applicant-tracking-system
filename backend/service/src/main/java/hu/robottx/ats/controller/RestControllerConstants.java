package hu.robottx.ats.controller;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RestControllerConstants {

    public static final String USERS_ENDPOINT = "/users";
    public static final String CURRENT_USER_ENDPOINT = USERS_ENDPOINT + "/me";
    public static final String COMPANIES_ENDPOINT = "/companies";
    public static final String COMPANY_BY_ID_ENDPOINT = COMPANIES_ENDPOINT + "/{id}";
    public static final String EMPLOYEES_ENDPOINT = COMPANIES_ENDPOINT + "/{companyId}/employees";
    public static final String EMPLOYEE_BY_ID_ENDPOINT = EMPLOYEES_ENDPOINT + "/{employeeId}";
    public static final String JOB_LISTINGS_ENDPOINT = "/job-listings";
    public static final String JOB_LISTING_BY_ID_ENDPOINT = JOB_LISTINGS_ENDPOINT + "/{jobListingId}";
    public static final String EMAIL_TEMPLATES_ENDPOINT = COMPANIES_ENDPOINT + "/{companyId}/email-templates";
    public static final String EMAIL_TEMPLATE_BY_ID_ENDPOINT = EMAIL_TEMPLATES_ENDPOINT + "/{emailTemplateId}";

}
