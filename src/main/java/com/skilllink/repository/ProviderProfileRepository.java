package com.skilllink.repository;

import com.skilllink.entity.ProviderProfile;
import com.skilllink.enums.ProviderApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderProfileRepository
        extends JpaRepository<ProviderProfile, Long> {

    Optional<ProviderProfile> findByUserId(Long userId);

    Optional<ProviderProfile> findByUserEmail(String email);

    List<ProviderProfile> findByApprovalStatus(
            ProviderApprovalStatus approvalStatus
    );
}