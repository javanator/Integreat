package com.integreat.integreatme.config.repositories;

import com.integreat.integreatme.models.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
    // Custom query methods can be added here
}
