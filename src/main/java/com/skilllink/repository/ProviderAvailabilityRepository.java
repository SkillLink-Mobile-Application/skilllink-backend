
package com.skilllink.repository;

import com.skilllink.entity.ProviderAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface ProviderAvailabilityRepository
        extends JpaRepository<ProviderAvailability, Long> {

    List<ProviderAvailability> findByProviderId(Long providerId);

    List<ProviderAvailability> findByProviderIdAndDayOfWeekAndAvailableTrue(
            Long providerId,
            DayOfWeek dayOfWeek
    );
}
