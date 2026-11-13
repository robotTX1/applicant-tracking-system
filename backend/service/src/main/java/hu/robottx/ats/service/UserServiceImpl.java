package hu.robottx.ats.service;

import com.oracle.bmc.util.internal.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import hu.robottx.ats.domain.CompanyPermission;
import hu.robottx.ats.domain.DocumentDownload;
import hu.robottx.ats.entity.Document;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.mapper.DocumentMapper;
import hu.robottx.ats.mapper.UserMapper;
import hu.robottx.ats.model.CurrentUserResponse;
import hu.robottx.ats.model.DocumentResponse;
import hu.robottx.ats.model.UserResponse;
import hu.robottx.ats.repository.UserRepository;
import hu.robottx.ats.security.service.CompanySecurityService;
import hu.robottx.ats.security.service.SecurityService;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String CV_KEY_PREFIX = "cv";
    private static final String USER_NOT_FOUND_BY_EMAIL_MESSAGE = "No user with this email";

    private final UserRepository userRepository;
    private final DocumentService documentService;
    private final SecurityService securityService;
    private final UserSyncService userSyncService;
    private final CompanySecurityService companySecurityService;

    @Override
    @Transactional(readOnly = true)
    public UserResponse lookupUserByEmail(String email) {
        String userId = securityService.getId();
        if (companySecurityService.getCompanyIdsWithPermission(CompanyPermission.MANAGE_EMPLOYEES).isEmpty()) {
            log.debug("User {} cannot look up users: does not manage any company team", userId);
            throw new AuthorizationDeniedException("Only users who manage a company team can look up users");
        }
        if (StringUtils.isBlank(email)) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        log.debug("Looking up user by email for user {}", userId);
        return userRepository.findFirstByEmailIgnoreCase(email.trim())
                .map(UserMapper.INSTANCE::toUserResponse)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_BY_EMAIL_MESSAGE));
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        User user = userSyncService.syncCurrentUser();
        return UserMapper.INSTANCE.toModel(user);
    }

    @Override
    @Transactional
    public DocumentResponse uploadCurrentUserCv(MultipartFile file) {
        User user = getCurrentUserEntity();
        Document savedDocument = documentService.uploadCvDocument(CV_KEY_PREFIX, user.getUserId(), file);
        Document oldCv = user.getCvDocument();
        user.setCvDocument(savedDocument);
        userRepository.save(user);
        if (oldCv != null) {
            documentService.deleteDocument(oldCv);
        }
        return DocumentMapper.INSTANCE.toModel(savedDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDownload getCurrentUserCv() {
        User user = getCurrentUserEntity();
        Document cv = getRequiredCv(user);
        return documentService.downloadDocument(cv);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getCurrentUserCvMetadata() {
        User user = getCurrentUserEntity();
        Document cv = getRequiredCv(user);
        return DocumentMapper.INSTANCE.toModel(cv);
    }

    @Override
    @Transactional
    public void deleteCurrentUserCv() {
        User user = getCurrentUserEntity();
        Document cv = getRequiredCv(user);
        user.setCvDocument(null);
        userRepository.save(user);
        documentService.deleteDocument(cv);
    }

    private User getCurrentUserEntity() {
        String userId = getRequiredField(securityService.getId(), "User ID cannot be empty");
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: : " + userId));
    }

    private Document getRequiredCv(User user) {
        Document cv = user.getCvDocument();
        if (cv == null) {
            throw new ResourceNotFoundException("CV not found for user: " + user.getUserId());
        }
        return cv;
    }

    private String getRequiredField(String value, String errorMessage) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

}
