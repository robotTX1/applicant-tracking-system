package hu.robottx.ats.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.domain.EmployeeRole;
import hu.robottx.ats.domain.EmployeeStatus;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.entity.Employee;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.model.EmployeeResponse;

class EmployeeMapperTest extends UnitTest {

    @Test
    @DisplayName("employee model includes the user's name and email")
    void employeeModelIncludesUser() {
        Employee employee = Employee.builder()
                .employeeId(3L)
                .company(Company.builder().companyId(10L).build())
                .user(User.builder().userId("user-1").firstName("John").lastName("Doe")
                        .email("john@example.com").build())
                .role(EmployeeRole.HR_MANAGER)
                .status(EmployeeStatus.ACTIVE)
                .build();

        EmployeeResponse response = EmployeeMapper.INSTANCE.toModel(employee);

        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
    }

}
