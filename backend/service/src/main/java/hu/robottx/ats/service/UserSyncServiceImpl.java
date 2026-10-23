package hu.robottx.ats.service;

import com.oracle.bmc.util.internal.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hu.robottx.ats.entity.User;
import hu.robottx.ats.repository.UserRepository;
import hu.robottx.ats.security.service.SecurityService;

@Slf4j
@Service
@CacheConfig(cacheNames = "provisionedUsers")
@RequiredArgsConstructor
public class UserSyncServiceImpl implements UserSyncService {

    private final UserRepository userRepository;
    private final SecurityService securityService;

    @Override
    @Transactional
    @Cacheable(key = "@securityService.getId()", condition = "@securityService.getId() != null")
    public User syncCurrentUser() {
        String userId = getRequiredField(securityService.getId(), "User ID cannot be empty");
        String firstName = getRequiredField(securityService.getFirstname(), "First name cannot be empty");
        String lastName = getRequiredField(securityService.getLastName(), "Last name cannot be empty");
        String email = getRequiredField(securityService.getEmail(), "Email cannot be empty");
        String phoneNumber = getRequiredField(securityService.getPhoneNumber(), "Phone number cannot be empty");
        User user = userRepository.findById(userId)
                .orElseGet(User::new);
        user.setUserId(userId);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPhoneNumber(phoneNumber);
        return userRepository.save(user);
    }

    private String getRequiredField(String value, String errorMessage) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

}
