package com.skilllink.repository;

import com.skilllink.entity.ProviderProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProviderProfileRepository
        extends JpaRepository<ProviderProfile, Long> {

    Optional<ProviderProfile> findByUserId(Long userId);

    Optional<ProviderProfile> findByUserEmail(String email);
}