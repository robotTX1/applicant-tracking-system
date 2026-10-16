package hu.robottx.ats.service;

import hu.robottx.ats.model.CompanyResponse;
import hu.robottx.ats.model.CompanyRole;
import hu.robottx.ats.model.CompanySaveRequest;
import hu.robottx.ats.model.CompanyUpdateRequest;
import hu.robottx.ats.model.PagedCompanyResponse;
import hu.robottx.ats.model.SearchAndPageRequest;

public interface CompanyService {

    PagedCompanyResponse getCompanies(SearchAndPageRequest request, Boolean isEmployee, CompanyRole role);

    CompanyResponse getCompanyById(Long companyId);

    CompanyResponse saveCompany(CompanySaveRequest request);

    CompanyResponse updateCompany(Long companyId, CompanyUpdateRequest request);

    void deleteCompany(Long companyId);

}
