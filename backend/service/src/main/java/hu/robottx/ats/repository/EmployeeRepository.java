package hu.robottx.ats.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hu.robottx.ats.domain.EmployeeRole;
import hu.robottx.ats.domain.EmployeeStatus;
import hu.robottx.ats.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    int countByUserUserIdAndRole(String userId, EmployeeRole role);

    boolean existsByCompanyCompanyIdAndUserUserIdAndRoleInAndStatus(
            Long companyId, String userId, Collection<EmployeeRole> roles, EmployeeStatus status);

    Optional<Employee> findByCompanyCompanyIdAndUserUserId(Long companyId, String userId);

    Optional<Employee> findByCompanyCompanyIdAndUserUserIdAndStatus(
            Long companyId, String userId, EmployeeStatus status);

    Optional<Employee> findByEmployeeIdAndCompanyCompanyId(Long employeeId, Long companyId);

    @Query("""
            SELECT e.company.companyId FROM Employee e
            WHERE e.user.userId = :userId
              AND e.role IN :roles
              AND e.status = :status
            """)
    Set<Long> findCompanyIdsByUserIdAndRolesAndStatus(
            @Param("userId") String userId,
            @Param("roles") Collection<EmployeeRole> roles,
            @Param("status") EmployeeStatus status);

    @Query("""
            SELECT e.company.companyId FROM Employee e
            WHERE e.user.userId = :userId
              AND e.status = :status
            """)
    Set<Long> findCompanyIdsByUserIdAndStatus(
            @Param("userId") String userId,
            @Param("status") EmployeeStatus status);

}
