package hu.robottx.ats.controller;

import static hu.robottx.ats.controller.RestControllerConstants.COMPANY_BY_ID_ENDPOINT;

import java.net.URI;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import hu.robottx.ats.api.CompaniesApiDelegate;
import hu.robottx.ats.model.CompanyResponse;
import hu.robottx.ats.model.CompanyRole;
import hu.robottx.ats.model.CompanySaveRequest;
import hu.robottx.ats.model.CompanyUpdateRequest;
import hu.robottx.ats.model.PagedCompanyResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.service.CompanyService;

@Component
@RequiredArgsConstructor
public class CompaniesApiDelegateImpl implements CompaniesApiDelegate {

    private final CompanyService companyService;

    @Override
    public ResponseEntity<PagedCompanyResponse> getCompanies(SearchAndPageRequest searchAndPageRequest,
            Boolean isEmployee, CompanyRole role) {
        PagedCompanyResponse response = companyService.getCompanies(searchAndPageRequest, isEmployee, role);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<CompanyResponse> getCompany(Long companyId) {
        CompanyResponse response = companyService.getCompanyById(companyId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<CompanyResponse> saveCompany(CompanySaveRequest companySaveRequest) {
        CompanyResponse response = companyService.saveCompany(companySaveRequest);
        return ResponseEntity.created(buildUri(response.getId())).body(response);
    }

    @Override
    public ResponseEntity<CompanyResponse> updateCompany(Long companyId, CompanyUpdateRequest companyUpdateRequest) {
        CompanyResponse response = companyService.updateCompany(companyId, companyUpdateRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteCompany(Long companyId) {
        companyService.deleteCompany(companyId);
        return ResponseEntity.noContent().build();
    }

    private URI buildUri(Long companyId) {
        return UriComponentsBuilder.fromUriString(COMPANY_BY_ID_ENDPOINT)
                .buildAndExpand(companyId)
                .toUri();
    }

}
