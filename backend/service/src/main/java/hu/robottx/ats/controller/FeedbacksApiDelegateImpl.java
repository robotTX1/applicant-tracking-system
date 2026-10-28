package hu.robottx.ats.controller;

import static hu.robottx.ats.controller.RestControllerConstants.APPLICATION_FEEDBACK_BY_ID_ENDPOINT;

import java.net.URI;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import hu.robottx.ats.api.FeedbacksApiDelegate;
import hu.robottx.ats.model.FeedbackResponse;
import hu.robottx.ats.model.FeedbackSaveRequest;
import hu.robottx.ats.model.FeedbackUpdateRequest;
import hu.robottx.ats.model.PagedFeedbackResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.service.FeedbackService;

@Component
@RequiredArgsConstructor
public class FeedbacksApiDelegateImpl implements FeedbacksApiDelegate {

    private final FeedbackService feedbackService;

    @Override
    public ResponseEntity<PagedFeedbackResponse> getApplicationFeedbacks(
            Long applicationId, SearchAndPageRequest searchAndPageRequest) {
        return ResponseEntity.ok(feedbackService.getApplicationFeedbacks(applicationId, searchAndPageRequest));
    }

    @Override
    public ResponseEntity<FeedbackResponse> getApplicationFeedback(Long applicationId, Long feedbackId) {
        return ResponseEntity.ok(feedbackService.getApplicationFeedbackById(applicationId, feedbackId));
    }

    @Override
    public ResponseEntity<FeedbackResponse> saveApplicationFeedback(
            Long applicationId, FeedbackSaveRequest feedbackSaveRequest) {
        FeedbackResponse response = feedbackService.saveApplicationFeedback(applicationId, feedbackSaveRequest);
        return ResponseEntity.created(buildUri(applicationId, response.getId())).body(response);
    }

    @Override
    public ResponseEntity<FeedbackResponse> updateApplicationFeedback(
            Long applicationId, Long feedbackId, FeedbackUpdateRequest feedbackUpdateRequest) {
        return ResponseEntity.ok(
                feedbackService.updateApplicationFeedback(applicationId, feedbackId, feedbackUpdateRequest));
    }

    @Override
    public ResponseEntity<Void> deleteApplicationFeedback(Long applicationId, Long feedbackId) {
        feedbackService.deleteApplicationFeedback(applicationId, feedbackId);
        return ResponseEntity.noContent().build();
    }

    private URI buildUri(Long applicationId, Long feedbackId) {
        return UriComponentsBuilder.fromUriString(APPLICATION_FEEDBACK_BY_ID_ENDPOINT)
                .buildAndExpand(applicationId, feedbackId)
                .toUri();
    }

}
