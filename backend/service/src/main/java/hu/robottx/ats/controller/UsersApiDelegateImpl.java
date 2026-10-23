package hu.robottx.ats.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import hu.robottx.ats.api.UsersApiDelegate;
import hu.robottx.ats.domain.DocumentDownload;
import hu.robottx.ats.model.CurrentUserResponse;
import hu.robottx.ats.model.DocumentResponse;
import hu.robottx.ats.service.UserService;

@Component
@RequiredArgsConstructor
public class UsersApiDelegateImpl implements UsersApiDelegate {

    private final UserService userService;

    @Override
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    @Override
    public ResponseEntity<DocumentResponse> uploadCurrentUserCv(MultipartFile file) {
        DocumentResponse response = userService.uploadCurrentUserCv(file);
        URI location = URI.create(RestControllerConstants.CURRENT_USER_CV_ENDPOINT);
        return ResponseEntity.created(location).body(response);
    }

    @Override
    public ResponseEntity<Resource> getCurrentUserCv() {
        DocumentDownload download = userService.getCurrentUserCv();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.resource());
    }

    @Override
    public ResponseEntity<DocumentResponse> getCurrentUserCvMetadata() {
        return ResponseEntity.ok(userService.getCurrentUserCvMetadata());
    }

    @Override
    public ResponseEntity<Void> deleteCurrentUserCv() {
        userService.deleteCurrentUserCv();
        return ResponseEntity.noContent().build();
    }

}
