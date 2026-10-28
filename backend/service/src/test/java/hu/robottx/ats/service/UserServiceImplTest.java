package hu.robottx.ats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.model.CurrentUserResponse;
import hu.robottx.ats.repository.UserRepository;
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

}
