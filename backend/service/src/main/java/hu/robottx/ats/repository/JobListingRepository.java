package hu.robottx.ats.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import hu.robottx.ats.entity.JobListing;

public interface JobListingRepository extends JpaRepository<JobListing, Long>, JpaSpecificationExecutor<JobListing> {

}
