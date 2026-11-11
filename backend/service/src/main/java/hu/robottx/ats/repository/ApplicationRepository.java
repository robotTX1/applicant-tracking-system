package hu.robottx.ats.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.domain.JobStatus;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.repository.projection.JobListingApplicationCount;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    @NonNull
    @Override
    @EntityGraph(attributePaths = "user")
    Page<Application> findAll(Specification<Application> spec, @NonNull Pageable pageable);

    long countByJobListingJobListingId(Long jobListingId);

    @Query("""
            SELECT a.jobListing.jobListingId AS jobListingId, COUNT(a) AS applicationCount \
            FROM Application a WHERE a.jobListing.jobListingId IN :jobListingIds \
            GROUP BY a.jobListing.jobListingId
            """)
    List<JobListingApplicationCount> countByJobListingIds(@Param("jobListingIds") Collection<Long> jobListingIds);

    boolean existsByJobListingJobListingIdAndUserUserId(Long jobListingId, String userId);

    Optional<Application> findByApplicationIdAndUserUserId(Long applicationId, String userId);

    Optional<Application> findByApplicationIdAndJobListingJobListingId(Long applicationId, Long jobListingId);

    @EntityGraph(attributePaths = {"jobListing", "cvDocument"})
    @Query("""
            SELECT a FROM Application a WHERE a.status = :status \
            AND a.jobListing.status = :jobListingStatus \
            AND a.jobListing.aiReviewEnabled = true \
            ORDER BY a.createdAt ASC, a.applicationId ASC
            """)
    List<Application> findPendingForAiReview(
            @Param("status") ApplicationStatus status,
            @Param("jobListingStatus") JobStatus jobListingStatus,
            Pageable pageable);

}
