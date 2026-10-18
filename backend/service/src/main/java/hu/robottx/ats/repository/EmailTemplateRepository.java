package hu.robottx.ats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import hu.robottx.ats.domain.EmailTemplateType;
import hu.robottx.ats.entity.EmailTemplate;

public interface EmailTemplateRepository
        extends JpaRepository<EmailTemplate, Long>, JpaSpecificationExecutor<EmailTemplate> {

    Optional<EmailTemplate> findByEmailTemplateIdAndCompanyCompanyId(Long emailTemplateId, Long companyId);

    long countByCompanyCompanyIdAndType(Long companyId, EmailTemplateType type);

}
