package hu.robottx.ats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.repository.UserRepository;
import hu.robottx.ats.security.service.SecurityService;

class UserSyncServiceImplTest extends UnitTest {

    private static final String USER_ID = "test-user-id";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final String EMAIL = "john.doe@example.com";
    private static final String PHONE_NUMBER = "+1234567890";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private UserSyncServiceImpl userSyncService;

    @Test
    @DisplayName("syncCurrentUser creates and saves new user when user does not exist in repository")
    void syncCurrentUserNewUser() {
        mockSecurityService();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User result = userSyncService.syncCurrentUser();
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getFirstName()).isEqualTo(FIRST_NAME);
        assertThat(result.getLastName()).isEqualTo(LAST_NAME);
        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.getPhoneNumber()).isEqualTo(PHONE_NUMBER);
        verify(userRepository).save(result);
    }

    @Test
    @DisplayName("syncCurrentUser updates existing user fields and saves")
    void syncCurrentUserExistingUser() {
        mockSecurityService();
        User existingUser = User.builder()
                .userId(USER_ID)
                .firstName("OldFirst")
                .lastName("OldLast")
                .email("old@example.com")
                .phoneNumber("+0000000000")
                .build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User result = userSyncService.syncCurrentUser();
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getFirstName()).isEqualTo(FIRST_NAME);
        assertThat(result.getLastName()).isEqualTo(LAST_NAME);
        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.getPhoneNumber()).isEqualTo(PHONE_NUMBER);
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("syncCurrentUser throws IllegalArgumentException when user id is blank")
    void syncCurrentUserMissingId() {
        when(securityService.getId()).thenReturn("");
        assertThatThrownBy(() -> userSyncService.syncCurrentUser())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID cannot be empty");
    }

    @Test
    @DisplayName("syncCurrentUser throws IllegalArgumentException when first name is blank")
    void syncCurrentUserMissingFirstName() {
        when(securityService.getId()).thenReturn(USER_ID);
        when(securityService.getFirstname()).thenReturn(" ");
        assertThatThrownBy(() -> userSyncService.syncCurrentUser())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("First name cannot be empty");
    }

    @Test
    @DisplayName("syncCurrentUser throws IllegalArgumentException when last name is blank")
    void syncCurrentUserMissingLastName() {
        when(securityService.getId()).thenReturn(USER_ID);
        when(securityService.getFirstname()).thenReturn(FIRST_NAME);
        when(securityService.getLastName()).thenReturn(null);
        assertThatThrownBy(() -> userSyncService.syncCurrentUser())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Last name cannot be empty");
    }

    @Test
    @DisplayName("syncCurrentUser throws IllegalArgumentException when email is blank")
    void syncCurrentUserMissingEmail() {
        when(securityService.getId()).thenReturn(USER_ID);
        when(securityService.getFirstname()).thenReturn(FIRST_NAME);
        when(securityService.getLastName()).thenReturn(LAST_NAME);
        when(securityService.getEmail()).thenReturn("");
        assertThatThrownBy(() -> userSyncService.syncCurrentUser())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email cannot be empty");
    }

    @Test
    @DisplayName("syncCurrentUser throws IllegalArgumentException when phone number is blank")
    void syncCurrentUserMissingPhoneNumber() {
        when(securityService.getId()).thenReturn(USER_ID);
        when(securityService.getFirstname()).thenReturn(FIRST_NAME);
        when(securityService.getLastName()).thenReturn(LAST_NAME);
        when(securityService.getEmail()).thenReturn(EMAIL);
        when(securityService.getPhoneNumber()).thenReturn(null);
        assertThatThrownBy(() -> userSyncService.syncCurrentUser())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phone number cannot be empty");
    }

    private void mockSecurityService() {
        when(securityService.getId()).thenReturn(USER_ID);
        when(securityService.getFirstname()).thenReturn(FIRST_NAME);
        when(securityService.getLastName()).thenReturn(LAST_NAME);
        when(securityService.getEmail()).thenReturn(EMAIL);
        when(securityService.getPhoneNumber()).thenReturn(PHONE_NUMBER);
    }

}
