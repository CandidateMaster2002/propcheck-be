package com.propchk.be.repository;

import com.propchk.be.entity.Role;
import com.propchk.be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    boolean existsByRole(Role role);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);
}
