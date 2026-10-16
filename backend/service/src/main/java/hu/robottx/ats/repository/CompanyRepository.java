package hu.robottx.ats.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hu.robottx.ats.domain.EmployeeRole;
import hu.robottx.ats.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {

    boolean existsByName(String name);

    @Query("""
            SELECT c FROM Company c
            JOIN Employee e ON e.company = c
            WHERE c.companyId = :companyId
              AND e.user.userId = :userId
              AND e.role IN :roles
            """)
    Optional<Company> findByCompanyIdAndEmployeeUserIdAndRoleIn(
            @Param("companyId") Long companyId,
            @Param("userId") String userId,
            @Param("roles") Collection<EmployeeRole> roles);

    @Modifying
    @Query("""
            DELETE FROM Company c
            WHERE c.companyId = :companyId
              AND EXISTS (
                  SELECT 1 FROM Employee e
                  WHERE e.company = c
                    AND e.user.userId = :userId
                    AND e.role = :role
              )
            """)
    int deleteByCompanyIdAndEmployeeUserIdAndRole(
            @Param("companyId") Long companyId,
            @Param("userId") String userId,
            @Param("role") EmployeeRole role);

}
