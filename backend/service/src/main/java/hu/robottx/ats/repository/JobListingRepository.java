package hu.robottx.ats.repository;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hu.robottx.ats.entity.JobListing;

public interface JobListingRepository extends JpaRepository<JobListing, Long>, JpaSpecificationExecutor<JobListing> {

    /** Fetches the company in the same query: listings are always returned with company details. */
    @Override
    @EntityGraph(attributePaths = "company")
    @NonNull
    Page<JobListing> findAll(Specification<JobListing> spec, @NonNull Pageable pageable);

    boolean existsByOfferEmailTemplateEmailTemplateIdOrDeclineEmailTemplateEmailTemplateId(
            Long offerEmailTemplateId, Long declineEmailTemplateId);

    @Modifying
    @Query("DELETE FROM JobListing j WHERE j.company.companyId = :companyId")
    void deleteByCompanyId(@Param("companyId") Long companyId);

}
