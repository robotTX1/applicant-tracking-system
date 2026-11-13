package hu.robottx.ats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authorization.AuthorizationDeniedException;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.domain.CompanyPermission;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.model.CurrentUserResponse;
import hu.robottx.ats.model.UserResponse;
import hu.robottx.ats.repository.UserRepository;
import hu.robottx.ats.security.service.CompanySecurityService;
import hu.robottx.ats.security.service.SecurityService;

class UserServiceImplTest extends UnitTest {

    private static final String USER_ID = "test-user-id";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final String EMAIL = "john.doe@example.com";
    private static final String PHONE_NUMBER = "+1234567890";

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentService documentService;

    @Mock
    private SecurityService securityService;

    @Mock
    private UserSyncService userSyncService;

    @Mock
    private CompanySecurityService companySecurityService;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("getCurrentUser delegates to userSyncService and returns CurrentUserResponse")
    void getCurrentUserDelegatesToUserSyncService() {
        User syncedUser = User.builder()
                .userId(USER_ID)
                .firstName(FIRST_NAME)
                .lastName(LAST_NAME)
                .email(EMAIL)
                .phoneNumber(PHONE_NUMBER)
                .build();
        when(userSyncService.syncCurrentUser()).thenReturn(syncedUser);
        CurrentUserResponse response = userService.getCurrentUser();
        assertThat(response).isNotNull();
        assertThat(response.getFirstName()).isEqualTo(FIRST_NAME);
        assertThat(response.getLastName()).isEqualTo(LAST_NAME);
        assertThat(response.getEmail()).isEqualTo(EMAIL);
        assertThat(response.getPhoneNumber()).isEqualTo(PHONE_NUMBER);
        verify(userSyncService).syncCurrentUser();
    }

    @Test
    @DisplayName("lookupUserByEmail returns the exact (trimmed) email match for team managers")
    void lookupUserByEmailReturnsMatchForTeamManagers() {
        User user = User.builder().userId(USER_ID).firstName(FIRST_NAME).lastName(LAST_NAME).email(EMAIL).build();
        when(companySecurityService.getCompanyIdsWithPermission(CompanyPermission.MANAGE_EMPLOYEES))
                .thenReturn(Set.of(1L));
        when(userRepository.findFirstByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        UserResponse response = userService.lookupUserByEmail("  " + EMAIL + " ");
        assertThat(response.getId()).isEqualTo(USER_ID);
        assertThat(response.getFirstName()).isEqualTo(FIRST_NAME);
        assertThat(response.getLastName()).isEqualTo(LAST_NAME);
        assertThat(response.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("lookupUserByEmail is forbidden for users who don't manage any company team")
    void lookupUserByEmailForbiddenWithoutTeamManagement() {
        when(companySecurityService.getCompanyIdsWithPermission(CompanyPermission.MANAGE_EMPLOYEES))
                .thenReturn(Set.of());
        assertThatThrownBy(() -> userService.lookupUserByEmail(EMAIL))
                .isInstanceOf(AuthorizationDeniedException.class);
        verify(userRepository, never()).findFirstByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("lookupUserByEmail throws not found when no user has the email")
    void lookupUserByEmailNotFound() {
        when(companySecurityService.getCompanyIdsWithPermission(CompanyPermission.MANAGE_EMPLOYEES))
                .thenReturn(Set.of(1L));
        when(userRepository.findFirstByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.lookupUserByEmail(EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("lookupUserByEmail rejects a blank email")
    void lookupUserByEmailRejectsBlank() {
        when(companySecurityService.getCompanyIdsWithPermission(CompanyPermission.MANAGE_EMPLOYEES))
                .thenReturn(Set.of(1L));
        assertThatThrownBy(() -> userService.lookupUserByEmail("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
