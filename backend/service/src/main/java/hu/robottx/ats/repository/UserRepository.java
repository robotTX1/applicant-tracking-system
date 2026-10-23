package hu.robottx.ats.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import hu.robottx.ats.entity.User;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

}
