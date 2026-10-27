package com.skilllink.controller;

import com.skilllink.entity.ProviderAvailability;
import com.skilllink.entity.User;
import com.skilllink.repository.ProviderAvailabilityRepository;
import com.skilllink.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.util.List;

@RestController
@RequestMapping("/api/provider-availability")
@CrossOrigin
public class ProviderAvailabilityController {

    private final ProviderAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;

    public ProviderAvailabilityController(
            ProviderAvailabilityRepository availabilityRepository,
            UserRepository userRepository) {
        this.availabilityRepository = availabilityRepository;
        this.userRepository = userRepository;
    }

    // Get provider's weekly availability
    @GetMapping
    public List<ProviderAvailability> getAvailability(
            @RequestParam String email) {

        User provider = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Provider not found"));

        return availabilityRepository.findByProviderId(provider.getId());
    }

    // Add a weekly availability slot
    @PostMapping
    public ProviderAvailability addAvailability(
            @RequestParam String email,
            @RequestParam DayOfWeek dayOfWeek,
            @RequestParam String startTime,
            @RequestParam String endTime) {

        User provider = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Provider not found"));

        if (!java.time.LocalTime.parse(startTime)
                .isBefore(java.time.LocalTime.parse(endTime))) {
            throw new RuntimeException(
                    "End time must be after start time");
        }

        ProviderAvailability availability = new ProviderAvailability();
        availability.setProvider(provider);
        availability.setDayOfWeek(dayOfWeek);
        availability.setStartTime(
                java.time.LocalTime.parse(startTime));
        availability.setEndTime(
                java.time.LocalTime.parse(endTime));
        availability.setAvailable(true);

        return availabilityRepository.save(availability);
    }

    // Enable or disable an availability slot
    @PutMapping("/{id}/availability")
    public ProviderAvailability updateAvailability(
            @PathVariable Long id,
            @RequestParam String email,
            @RequestParam boolean available) {

        User provider = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Provider not found"));

        ProviderAvailability slot =
                availabilityRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Availability not found"));

        if (!slot.getProvider().getId()
                .equals(provider.getId())) {
            throw new RuntimeException(
                    "You cannot update another provider's availability");
        }

        slot.setAvailable(available);
        return availabilityRepository.save(slot);
    }
}