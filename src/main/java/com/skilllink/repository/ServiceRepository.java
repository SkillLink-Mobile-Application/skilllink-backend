package com.skilllink.repository;

import com.skilllink.entity.Service;
import com.skilllink.enums.ProviderApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findByProviderEmail(String email);

    List<Service> findByCategory(String category);

    @Query("""
        SELECT s FROM Service s
        WHERE s.category = :category
        AND EXISTS (
            SELECT p.id FROM ProviderProfile p
            WHERE p.user = s.provider
            AND p.approvalStatus = :approvalStatus
        )
    """)
    List<Service> findApprovedServicesByCategory(
            @Param("category") String category,
            @Param("approvalStatus") ProviderApprovalStatus approvalStatus
    );
}