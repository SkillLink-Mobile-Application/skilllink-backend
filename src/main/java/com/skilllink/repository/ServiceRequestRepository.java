package com.skilllink.repository;

import com.skilllink.entity.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ServiceRequestRepository
        extends JpaRepository<ServiceRequest, Long> {

    List<ServiceRequest> findByCustomerEmail(String email);

    List<ServiceRequest> findByProviderEmail(String email);

    boolean existsByProviderIdAndRequestedDateAndRequestedTimeAndStatusIn(
            Long providerId,
            LocalDate requestedDate,
            LocalTime requestedTime,
            List<String> statuses
    );

}

