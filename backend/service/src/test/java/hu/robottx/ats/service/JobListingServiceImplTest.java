package hu.robottx.ats.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.domain.CompanyPermission;
import hu.robottx.ats.domain.JobStatus;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.model.JobListingResponse;
import hu.robottx.ats.model.PagedJobListingResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.repository.ApplicationRepository;
import hu.robottx.ats.repository.CompanyRepository;
import hu.robottx.ats.repository.EmailTemplateRepository;
import hu.robottx.ats.repository.JobListingRepository;
import hu.robottx.ats.repository.projection.JobListingApplicationCount;
import hu.robottx.ats.security.service.CompanySecurityService;
import hu.robottx.ats.security.service.SecurityService;

class JobListingServiceImplTest extends UnitTest {

    @Mock
    private SecurityService securityService;
    @Mock
    private CompanySecurityService companySecurityService;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private EmailTemplateRepository emailTemplateRepository;
    @Mock
    private JobListingRepository jobListingRepository;
    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private JobListingServiceImpl jobListingService;

    private static final Company OWN = company(10L, "Acme");
    private static final Company OTHER = company(20L, "Globex");

    private static Company company(long id, String name) {
        return Company.builder().companyId(id).name(name).foundingDate(LocalDate.of(2020, 1, 1))
                .websiteUrl("https://example.com").description("d").build();
    }

    private static JobListing jobListing(long id, Company company) {
        return JobListing.builder().jobListingId(id).company(company).title("Job " + id)
                .location("Budapest").description("d").status(JobStatus.PUBLISHED).build();
    }

    private static SearchAndPageRequest firstPage() {
        SearchAndPageRequest request = new SearchAndPageRequest();
        request.setPageNumber(0);
        request.setPageSize(20);
        return request;
    }

    private static JobListingApplicationCount count(long jobListingId, long count) {
        return new JobListingApplicationCount() {
            @Override
            public Long getJobListingId() {
                return jobListingId;
            }

            @Override
            public Long getApplicationCount() {
                return count;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void returnPage(List<JobListing> listings) {
        when(jobListingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(listings, PageRequest.of(0, 20), listings.size()));
    }

    @Test
    @DisplayName("getJobListings counts applications only for listings of the user's companies")
    void getJobListingsCountsOwnListings() {
        when(securityService.getId()).thenReturn("user-1");
        when(companySecurityService.getCompanyIdsWithPermission(CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS))
                .thenReturn(Set.of(10L));
        when(companySecurityService.getActiveCompanyIds()).thenReturn(Set.of(10L));
        returnPage(List.of(jobListing(1L, OWN), jobListing(2L, OWN), jobListing(3L, OTHER)));
        when(applicationRepository.countByJobListingIds(Set.of(1L, 2L))).thenReturn(List.of(count(1L, 5L)));

        PagedJobListingResponse response = jobListingService.getJobListings(firstPage());

        assertThat(response.getContent())
                .extracting(JobListingResponse::getId, JobListingResponse::getApplicationCount,
                        jl -> jl.getCompany().getName())
                .containsExactly(tuple(1L, 5L, "Acme"), tuple(2L, 0L, "Acme"), tuple(3L, null, "Globex"));
    }

    @Test
    @DisplayName("getJobListings does not query counts for users without companies")
    void getJobListingsSkipsCountsForCandidates() {
        when(securityService.getId()).thenReturn("user-1");
        when(companySecurityService.getCompanyIdsWithPermission(CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS))
                .thenReturn(Set.of());
        when(companySecurityService.getActiveCompanyIds()).thenReturn(Set.of());
        returnPage(List.of(jobListing(3L, OTHER)));

        PagedJobListingResponse response = jobListingService.getJobListings(firstPage());

        assertThat(response.getContent().getFirst().getApplicationCount()).isNull();
        assertThat(response.getContent().getFirst().getCompany().getName()).isEqualTo("Globex");
        verify(applicationRepository, never()).countByJobListingIds(anyCollection());
    }

    @Test
    @DisplayName("getJobListingById includes the application count for employees")
    void getJobListingByIdIncludesCountForEmployees() {
        when(securityService.getId()).thenReturn("user-1");
        when(jobListingRepository.findById(1L)).thenReturn(Optional.of(jobListing(1L, OWN)));
        when(companySecurityService.hasPermission(10L, CompanyPermission.VIEW_EMPLOYEES)).thenReturn(true);
        when(applicationRepository.countByJobListingJobListingId(1L)).thenReturn(3L);

        JobListingResponse response = jobListingService.getJobListingById(1L);

        assertThat(response.getApplicationCount()).isEqualTo(3L);
        assertThat(response.getCompany().getName()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("getJobListingById hides the application count from candidates")
    void getJobListingByIdHidesCountForCandidates() {
        when(securityService.getId()).thenReturn("user-1");
        when(jobListingRepository.findById(3L)).thenReturn(Optional.of(jobListing(3L, OTHER)));
        when(companySecurityService.hasPermission(20L, CompanyPermission.VIEW_EMPLOYEES)).thenReturn(false);

        JobListingResponse response = jobListingService.getJobListingById(3L);

        assertThat(response.getApplicationCount()).isNull();
        assertThat(response.getCompany().getName()).isEqualTo("Globex");
        verify(applicationRepository, never()).countByJobListingJobListingId(any());
    }

}
