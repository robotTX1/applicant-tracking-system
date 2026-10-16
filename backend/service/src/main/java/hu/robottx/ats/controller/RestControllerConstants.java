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

}
