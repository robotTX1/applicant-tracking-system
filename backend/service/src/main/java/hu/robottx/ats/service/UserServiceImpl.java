package hu.robottx.ats.service;

import com.oracle.bmc.util.internal.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
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
import hu.robottx.ats.model.PagedUserResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.repository.UserRepository;
import hu.robottx.ats.security.service.SecurityService;
import hu.robottx.ats.util.UserQueryParamsUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String CV_KEY_PREFIX = "cv";

    private final UserRepository userRepository;
    private final DocumentService documentService;
    private final SecurityService securityService;
    private final UserSyncService userSyncService;

    @Override
    @Transactional(readOnly = true)
    public PagedUserResponse getUsers(SearchAndPageRequest request) {
        log.debug("Getting users with request: {}", request);
        Specification<User> spec = UserQueryParamsUtil.createSearchSpecification(request);
        int pageNumber = request != null && request.getPageNumber() != null ? request.getPageNumber() : 0;
        int pageSize = request != null && request.getPageSize() != null ? request.getPageSize() : 20;
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize);
        try {
            Page<User> users = userRepository.findAll(spec, pageRequest);
            return UserMapper.INSTANCE.toPagedUserResponse(users);
        } catch (RuntimeException ex) {
            log.debug("Invalid query or sort parameters: query='{}', sort='{}'",
                    request != null ? request.getQuery() : null, request != null ? request.getSort() : null);
            throw new IllegalArgumentException("Invalid query or sort parameters", ex);
        }
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
