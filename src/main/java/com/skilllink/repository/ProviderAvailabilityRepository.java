
package com.skilllink.repository;

import com.skilllink.entity.ProviderAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public interface ProviderAvailabilityRepository
        extends JpaRepository<ProviderAvailability, Long> {

    // Get all availability entries for a provider
    List<ProviderAvailability> findByProviderId(Long providerId);

    // Weekly recurring availability
    List<ProviderAvailability>
    findByProviderIdAndDayOfWeekAndAvailableTrue(
            Long providerId,
            DayOfWeek dayOfWeek
    );

    // All entries for a specific date
    List<ProviderAvailability>
    findByProviderIdAndSpecificDate(
            Long providerId,
            LocalDate specificDate
    );

    // Available entries for a specific date
    List<ProviderAvailability>
    findByProviderIdAndSpecificDateAndAvailableTrue(
            Long providerId,
            LocalDate specificDate
    );
}
