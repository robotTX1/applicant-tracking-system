package hu.robottx.ats.service;

import com.oracle.bmc.util.internal.StringUtils;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import hu.robottx.ats.domain.DocumentDownload;
import hu.robottx.ats.entity.Document;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.mapper.DocumentMapper;
import hu.robottx.ats.mapper.UserMapper;
import hu.robottx.ats.model.CurrentUserResponse;
import hu.robottx.ats.model.DocumentResponse;
import hu.robottx.ats.repository.UserRepository;
import hu.robottx.ats.security.service.SecurityService;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String CV_KEY_PREFIX = "cv";

    private final UserRepository userRepository;
    private final DocumentService documentService;
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
