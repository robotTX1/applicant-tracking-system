package hu.robottx.ats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import hu.robottx.ats.entity.Application;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    boolean existsByJobListingJobListingIdAndUserUserId(Long jobListingId, String userId);

    Optional<Application> findByApplicationIdAndUserUserId(Long applicationId, String userId);

    Optional<Application> findByApplicationIdAndJobListingJobListingId(Long applicationId, Long jobListingId);

}
