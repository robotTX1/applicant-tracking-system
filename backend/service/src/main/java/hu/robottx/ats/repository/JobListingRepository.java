package hu.robottx.ats.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hu.robottx.ats.entity.JobListing;

public interface JobListingRepository extends JpaRepository<JobListing, Long>, JpaSpecificationExecutor<JobListing> {

    boolean existsByOfferEmailTemplateEmailTemplateIdOrDeclineEmailTemplateEmailTemplateId(
            Long offerEmailTemplateId, Long declineEmailTemplateId);

    @Modifying
    @Query("DELETE FROM JobListing j WHERE j.company.companyId = :companyId")
    void deleteByCompanyId(@Param("companyId") Long companyId);

}
