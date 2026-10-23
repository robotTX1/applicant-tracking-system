package hu.robottx.ats.service;

import java.util.List;

import hu.robottx.ats.domain.AiReviewResult;
import hu.robottx.ats.entity.Application;

public interface ApplicationAiReviewService {

    List<Application> findApplicationsForAiReview(int batchSize);

    AiReviewResult evaluateApplication(Application application);

    void saveAiReviewResult(Long applicationId, AiReviewResult result);

}
