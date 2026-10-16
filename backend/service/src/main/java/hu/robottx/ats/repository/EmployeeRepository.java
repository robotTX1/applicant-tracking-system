package hu.robottx.ats.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import hu.robottx.ats.domain.EmployeeRole;
import hu.robottx.ats.domain.EmployeeStatus;
import hu.robottx.ats.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    int countByUserUserIdAndRole(String userId, EmployeeRole role);

    boolean existsByCompanyCompanyIdAndUserUserIdAndStatus(Long companyId, String userId, EmployeeStatus status);

    boolean existsByCompanyCompanyIdAndUserUserIdAndRoleInAndStatus(
            Long companyId, String userId, Collection<EmployeeRole> roles, EmployeeStatus status);

    Optional<Employee> findByCompanyCompanyIdAndUserUserId(Long companyId, String userId);

    Optional<Employee> findByEmployeeIdAndCompanyCompanyId(Long employeeId, Long companyId);

}
