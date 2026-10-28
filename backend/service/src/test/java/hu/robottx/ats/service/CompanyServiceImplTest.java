package hu.robottx.ats.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.entity.Employee;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.model.CompanyResponse;
import hu.robottx.ats.model.CompanySaveRequest;
import hu.robottx.ats.repository.CompanyRepository;
import hu.robottx.ats.repository.EmployeeRepository;
import hu.robottx.ats.repository.JobListingRepository;
import hu.robottx.ats.repository.UserRepository;
import hu.robottx.ats.security.service.CompanySecurityService;
import hu.robottx.ats.security.service.SecurityService;

class CompanyServiceImplTest extends UnitTest {

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private SecurityService securityService;

    @Mock
    private CompanySecurityService companySecurityService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private JobListingRepository jobListingRepository;

    @Mock
    private EmailTemplateService emailTemplateService;

    @InjectMocks
    private CompanyServiceImpl companyService;

    @Test
    @DisplayName("saveCompany creates owner employee and triggers default email template generation")
    void saveCompanyGeneratesDefaultEmailTemplates() {
        String userId = "user-123";
        when(securityService.getId()).thenReturn(userId);
        when(employeeRepository.countByUserUserIdAndRole(any(), any())).thenReturn(0);
        when(companyRepository.existsByName("TechCorp")).thenReturn(false);
        User user = User.builder().userId(userId).build();
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        Company savedCompany = Company.builder()
                .companyId(10L)
                .name("TechCorp")
                .foundingDate(LocalDate.of(2020, 1, 1))
                .websiteUrl("https://techcorp.example.com")
                .description("Tech company")
                .build();
        when(companyRepository.save(any(Company.class))).thenReturn(savedCompany);
        CompanySaveRequest request = new CompanySaveRequest();
        request.setName("TechCorp");
        request.setFoundingDate(LocalDate.of(2020, 1, 1));
        request.setWebsiteUrl("https://techcorp.example.com");
        request.setDescription("Tech company");
        CompanyResponse response = companyService.saveCompany(request);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        verify(employeeRepository).save(any(Employee.class));
        verify(emailTemplateService).createDefaultEmailTemplates(savedCompany);
    }

}
