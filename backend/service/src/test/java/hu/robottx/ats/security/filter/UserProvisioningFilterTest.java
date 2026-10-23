package hu.robottx.ats.security.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerExceptionResolver;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.service.UserSyncService;

class UserProvisioningFilterTest extends UnitTest {

    @Mock
    private UserSyncService userSyncService;

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private UserProvisioningFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilterInternal continues filter chain without sync when unauthenticated")
    void whenUnauthenticated_thenDoFilterWithoutSync() throws ServletException, IOException {
        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userSyncService);
        verifyNoInteractions(handlerExceptionResolver);
    }

    @Test
    @DisplayName("doFilterInternal continues filter chain without sync when anonymous authentication")
    void whenAnonymousAuth_thenDoFilterWithoutSync() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userSyncService);
        verifyNoInteractions(handlerExceptionResolver);
    }

    @Test
    @DisplayName("doFilterInternal syncs user and continues filter chain when valid JWT")
    void whenValidJwt_thenSyncUserAndDoFilter() throws ServletException, IOException {
        Jwt jwt = createValidJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        filter.doFilter(request, response, filterChain);
        verify(userSyncService).syncCurrentUser();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(handlerExceptionResolver);
    }

    @Test
    @DisplayName("doFilterInternal resolves exception and stops chain when syncCurrentUser throws IllegalArgument")
    void whenSyncThrowsIllegalArgumentException_thenResolveException() throws ServletException, IOException {
        Jwt jwt = createValidJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        when(userSyncService.syncCurrentUser()).thenThrow(new IllegalArgumentException("First name cannot be empty"));
        filter.doFilter(request, response, filterChain);
        verify(userSyncService).syncCurrentUser();
        verify(handlerExceptionResolver).resolveException(
                eq(request), eq(response), isNull(), any(IllegalArgumentException.class));
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal resolves exception and stops chain when syncCurrentUser throws generic exception")
    void whenSyncThrowsGenericException_thenResolveException() throws ServletException, IOException {
        Jwt jwt = createValidJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        when(userSyncService.syncCurrentUser()).thenThrow(new IllegalStateException("Database unavailable"));
        filter.doFilter(request, response, filterChain);
        verify(userSyncService).syncCurrentUser();
        verify(handlerExceptionResolver).resolveException(
                eq(request), eq(response), isNull(), any(IllegalStateException.class));
        verify(filterChain, never()).doFilter(request, response);
    }

    private Jwt createValidJwt() {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject("user-123")
                .claim("given_name", "John")
                .claim("family_name", "Doe")
                .claim("email", "john.doe@example.com")
                .claim("phone_number", "+1234567890")
                .build();
    }

}
