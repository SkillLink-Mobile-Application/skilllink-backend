
package com.skilllink.controller;

import com.skilllink.entity.ProviderAvailability;
import com.skilllink.entity.User;
import com.skilllink.repository.ProviderAvailabilityRepository;
import com.skilllink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
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

    private User getProvider(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("Provider not found"));
    }

    private ProviderAvailability getOwnedSlot(Long id, User provider) {
        ProviderAvailability slot = availabilityRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Availability not found"));

        if (slot.getProvider() == null
                || !slot.getProvider().getId().equals(provider.getId())) {
            throw new SecurityException(
                    "You cannot modify another provider's availability");
        }

        return slot;
    }

    private LocalTime[] validateTimes(String startTime, String endTime) {
        LocalTime start;
        LocalTime end;

        try {
            start = LocalTime.parse(startTime);
            end = LocalTime.parse(endTime);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid time. Use HH:mm format.");
        }

        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(
                    "End time must be after start time");
        }

        return new LocalTime[]{start, end};
    }

    // Get all availability entries
    @GetMapping
    public ResponseEntity<?> getAvailability(@RequestParam String email) {
        try {
            User provider = getProvider(email);
            return ResponseEntity.ok(
                    availabilityRepository.findByProviderId(provider.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // Add weekly recurring availability
    @PostMapping
    public ResponseEntity<?> addAvailability(
            @RequestParam String email,
            @RequestParam DayOfWeek dayOfWeek,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            User provider = getProvider(email);
            LocalTime[] times = validateTimes(startTime, endTime);

            ProviderAvailability slot = new ProviderAvailability();
            slot.setProvider(provider);
            slot.setDayOfWeek(dayOfWeek);
            slot.setSpecificDate(null);
            slot.setStartTime(times[0]);
            slot.setEndTime(times[1]);
            slot.setAvailable(true);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(availabilityRepository.save(slot));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Add availability for one specific date
    @PostMapping("/date")
    public ResponseEntity<?> addDateAvailability(
            @RequestParam String email,
            @RequestParam String date,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            User provider = getProvider(email);

            LocalDate specificDate;
            try {
                specificDate = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest()
                        .body("Invalid date. Use yyyy-MM-dd format.");
            }

            LocalTime[] times = validateTimes(startTime, endTime);

            // Prevent duplicate date/time entries.
            List<ProviderAvailability> existing =
                    availabilityRepository.findByProviderIdAndSpecificDate(
                            provider.getId(), specificDate);

            boolean overlaps = existing.stream().anyMatch(slot ->
                    times[0].isBefore(slot.getEndTime())
                            && times[1].isAfter(slot.getStartTime()));

            if (overlaps) {
                return ResponseEntity.badRequest()
                        .body("This time overlaps an existing slot for that date.");
            }

            ProviderAvailability slot = new ProviderAvailability();
            slot.setProvider(provider);
            slot.setDayOfWeek(null);
            slot.setSpecificDate(specificDate);
            slot.setStartTime(times[0]);
            slot.setEndTime(times[1]);
            slot.setAvailable(true);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(availabilityRepository.save(slot));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Edit an existing weekly or specific-date slot
    @PutMapping("/{id}")
    public ResponseEntity<?> editAvailability(
            @PathVariable Long id,
            @RequestParam String email,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) DayOfWeek dayOfWeek) {
        try {
            User provider = getProvider(email);
            ProviderAvailability slot = getOwnedSlot(id, provider);
            LocalTime[] times = validateTimes(startTime, endTime);

            if (slot.getSpecificDate() != null) {
                if (date != null && !date.isBlank()) {
                    slot.setSpecificDate(LocalDate.parse(date));
                }
                slot.setDayOfWeek(null);
            } else {
                if (dayOfWeek != null) {
                    slot.setDayOfWeek(dayOfWeek);
                }
                slot.setSpecificDate(null);
            }

            slot.setStartTime(times[0]);
            slot.setEndTime(times[1]);

            return ResponseEntity.ok(availabilityRepository.save(slot));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Enable or disable a slot
    @PutMapping("/{id}/availability")
    public ResponseEntity<?> updateAvailability(
            @PathVariable Long id,
            @RequestParam String email,
            @RequestParam boolean available) {
        try {
            User provider = getProvider(email);
            ProviderAvailability slot = getOwnedSlot(id, provider);

            slot.setAvailable(available);
            return ResponseEntity.ok(availabilityRepository.save(slot));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // Delete one slot
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAvailability(
            @PathVariable Long id,
            @RequestParam String email) {
        try {
            User provider = getProvider(email);
            ProviderAvailability slot = getOwnedSlot(id, provider);

            availabilityRepository.delete(slot);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
