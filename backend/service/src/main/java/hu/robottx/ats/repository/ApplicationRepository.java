package hu.robottx.ats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.domain.JobStatus;
import hu.robottx.ats.entity.Application;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    boolean existsByJobListingJobListingIdAndUserUserId(Long jobListingId, String userId);

    Optional<Application> findByApplicationIdAndUserUserId(Long applicationId, String userId);

    Optional<Application> findByApplicationIdAndJobListingJobListingId(Long applicationId, Long jobListingId);

    @EntityGraph(attributePaths = {"jobListing", "cvDocument"})
    @Query("SELECT a FROM Application a WHERE a.status = :status "
            + "AND a.jobListing.status = :jobListingStatus "
            + "AND a.jobListing.aiReviewEnabled = true "
            + "ORDER BY a.createdAt ASC, a.applicationId ASC")
    List<Application> findPendingForAiReview(
            @Param("status") ApplicationStatus status,
            @Param("jobListingStatus") JobStatus jobListingStatus,
            Pageable pageable);

}
