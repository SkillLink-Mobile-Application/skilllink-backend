package com.skilllink.repository;

import com.skilllink.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository
        extends JpaRepository<Review, Long> {

    Optional<Review> findByServiceRequestId(
            Long serviceRequestId
    );

    List<Review> findByProviderEmail(
            String email
    );

    List<Review> findByCustomerId(Long customerId);

    List<Review> findByProviderId(Long providerId);
}