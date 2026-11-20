package dev.bytebank.auth.user;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.bytebank.common.security.Role;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

}
