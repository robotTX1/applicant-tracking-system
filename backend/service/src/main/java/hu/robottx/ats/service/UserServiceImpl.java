package hu.robottx.ats.service;

import com.oracle.bmc.util.internal.StringUtils;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hu.robottx.ats.entity.User;
import hu.robottx.ats.mapper.UserMapper;
import hu.robottx.ats.model.CurrentUserResponse;
import hu.robottx.ats.repository.UserRepository;
import hu.robottx.ats.security.service.SecurityService;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final SecurityService securityService;

    @Override
    @Transactional
    public CurrentUserResponse getCurrentUser() {
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
        return UserMapper.INSTANCE.toModel(userRepository.save(user));
    }

    private String getRequiredField(String value, String errorMessage) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

}
