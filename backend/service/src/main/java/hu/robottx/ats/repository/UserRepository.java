package hu.robottx.ats.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import hu.robottx.ats.entity.User;

public interface UserRepository extends JpaRepository<User, String> {

}
