package hu.robottx.ats.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hu.robottx.ats.domain.CompanyPermission;
import hu.robottx.ats.domain.EmployeeRole;
import hu.robottx.ats.domain.EmployeeStatus;
import hu.robottx.ats.domain.ResourceLimit;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.entity.Employee;
import hu.robottx.ats.exception.ConflictException;
import hu.robottx.ats.exception.ResourceLimitException;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.mapper.CompanyMapper;
import hu.robottx.ats.model.CompanyResponse;
import hu.robottx.ats.model.CompanyRole;
import hu.robottx.ats.model.CompanySaveRequest;
import hu.robottx.ats.model.CompanyUpdateRequest;
import hu.robottx.ats.model.PagedCompanyResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.repository.CompanyRepository;
import hu.robottx.ats.repository.EmployeeRepository;
import hu.robottx.ats.repository.UserRepository;
import hu.robottx.ats.security.service.CompanySecurityService;
import hu.robottx.ats.security.service.SecurityService;
import hu.robottx.ats.util.CompanyQueryParamsUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private static final String COMPANY_LIMIT_MESSAGE = "Max company limit reached. Limit %d";
    private static final String COMPANY_ALREADY_EXISTS_MESSAGE = "Company already exists with name: %s";
    private static final String COMPANY_NOT_FOUND_MESSAGE = "No company with id: %s";

    private final Clock clock;
    private final SecurityService securityService;
    private final CompanySecurityService companySecurityService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedCompanyResponse getCompanies(SearchAndPageRequest request, Boolean isEmployee, CompanyRole role) {
        String userId = securityService.getId();
        log.debug("Getting companies for user {} with request {}, isEmployee={}, role={}",
                userId, request, isEmployee, role);
        Set<Long> userCompanyIds = getUserCompanyIds(isEmployee, role);
        PageRequest pageRequest = PageRequest.of(request.getPageNumber(), request.getPageSize());
        Specification<Company> searchSpec = CompanyQueryParamsUtil.createSearchSpecification(request);
        Specification<Company> employeeSpec = CompanyQueryParamsUtil.employeeSpec(userCompanyIds, isEmployee, role);
        Specification<Company> combinedSpec = searchSpec.and(employeeSpec);
        try {
            Page<Company> companies = companyRepository.findAll(combinedSpec, pageRequest);
            return CompanyMapper.INSTANCE.toModel(companies);
        } catch (RuntimeException ex) {
            log.debug("Invalid query or sort parameters provided by user {}: query='{}', sort='{}'",
                    userId, request.getQuery(), request.getSort());
            throw new IllegalArgumentException("Invalid query or sort parameters", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyResponse getCompanyById(Long companyId) {
        log.debug("Getting company with id {}", companyId);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> {
                    log.debug("No company with id {}", companyId);
                    return new ResourceNotFoundException(COMPANY_NOT_FOUND_MESSAGE.formatted(companyId));
                });
        return CompanyMapper.INSTANCE.toModel(company);
    }

    @Override
    @Transactional
    public CompanyResponse saveCompany(CompanySaveRequest request) {
        String userId = securityService.getId();
        log.debug("Saving company {} for user {}", request, userId);
        validateCompanyLimit(userId);
        validateCompanyNameNotExists(request.getName());
        Company company = CompanyMapper.INSTANCE.toEntity(request);
        company = companyRepository.save(company);
        Employee owner = Employee.builder()
                .user(userRepository.getReferenceById(userId))
                .company(company)
                .role(EmployeeRole.OWNER)
                .status(EmployeeStatus.ACTIVE)
                .assignedDate(Instant.now(clock))
                .build();
        employeeRepository.save(owner);
        log.debug("Saved company {} with id {} for user {}", company.getName(), company.getCompanyId(), userId);
        return CompanyMapper.INSTANCE.toModel(company);
    }

    @Override
    @Transactional
    public CompanyResponse updateCompany(Long companyId, CompanyUpdateRequest request) {
        String userId = securityService.getId();
        log.debug("Updating company {} for user {} with request {}", companyId, userId, request);
        companySecurityService.validatePermission(companyId, CompanyPermission.UPDATE_COMPANY);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> {
                    log.debug("No company with id {}", companyId);
                    return new ResourceNotFoundException(COMPANY_NOT_FOUND_MESSAGE.formatted(companyId));
                });
        if (!company.getName().equals(request.getName())) {
            validateCompanyNameNotExists(request.getName());
        }
        company = CompanyMapper.INSTANCE.updateEntity(request, company);
        company = companyRepository.save(company);
        log.debug("Updated company {} for user {}", companyId, userId);
        return CompanyMapper.INSTANCE.toModel(company);
    }

    @Override
    @Transactional
    public void deleteCompany(Long companyId) {
        String userId = securityService.getId();
        log.debug("Deleting company {} for user {}", companyId, userId);
        companySecurityService.validatePermission(companyId, CompanyPermission.DELETE_COMPANY);
        companyRepository.deleteById(companyId);
        log.debug("Deleted company {} for user {}", companyId, userId);
    }

    private Set<Long> getUserCompanyIds(Boolean isEmployee, CompanyRole role) {
        Set<Long> userCompanyIds = Collections.emptySet();
        if (isEmployee != null || role != null) {
            if (role != null) {
                EmployeeRole domainRole = CompanyMapper.INSTANCE.toDomain(role);
                userCompanyIds = companySecurityService.getCompanyIdsWithRoles(Set.of(domainRole));
            } else {
                userCompanyIds = companySecurityService.getActiveCompanyIds();
            }
        }
        return userCompanyIds;
    }

    private void validateCompanyLimit(String userId) {
        int currentCount = employeeRepository.countByUserUserIdAndRole(userId, EmployeeRole.OWNER);
        int limit = ResourceLimit.MAX_COMPANIES.getLimit();
        if (currentCount >= limit) {
            log.debug("User {} has reached the maximum limit of companies: {}", userId, limit);
            throw new ResourceLimitException(COMPANY_LIMIT_MESSAGE.formatted(limit));
        }
    }

    private void validateCompanyNameNotExists(String name) {
        if (companyRepository.existsByName(name)) {
            log.debug("Company with name {} already exists", name);
            throw new ConflictException(COMPANY_ALREADY_EXISTS_MESSAGE.formatted(name));
        }
    }

}
