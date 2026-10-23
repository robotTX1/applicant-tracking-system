package hu.robottx.ats.service;

import hu.robottx.ats.model.FeedbackResponse;
import hu.robottx.ats.model.FeedbackSaveRequest;
import hu.robottx.ats.model.FeedbackUpdateRequest;
import hu.robottx.ats.model.PagedFeedbackResponse;
import hu.robottx.ats.model.SearchAndPageRequest;

public interface FeedbackService {

    PagedFeedbackResponse getApplicationFeedbacks(Long applicationId, SearchAndPageRequest request);

    FeedbackResponse getApplicationFeedbackById(Long applicationId, Long feedbackId);

    FeedbackResponse saveApplicationFeedback(Long applicationId, FeedbackSaveRequest request);

    FeedbackResponse updateApplicationFeedback(Long applicationId, Long feedbackId, FeedbackUpdateRequest request);

    void deleteApplicationFeedback(Long applicationId, Long feedbackId);

}
