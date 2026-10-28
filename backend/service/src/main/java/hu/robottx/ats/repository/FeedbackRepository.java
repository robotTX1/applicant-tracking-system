package hu.robottx.ats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import hu.robottx.ats.domain.FeedbackType;
import hu.robottx.ats.entity.Feedback;

public interface FeedbackRepository extends JpaRepository<Feedback, Long>, JpaSpecificationExecutor<Feedback> {

    boolean existsByApplicationApplicationIdAndEmployeeEmployeeIdAndType(
            Long applicationId, Long employeeId, FeedbackType type);

    Optional<Feedback> findByFeedbackIdAndApplicationApplicationId(Long feedbackId, Long applicationId);

}
