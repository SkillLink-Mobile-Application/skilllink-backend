package com.skilllink.controller;

import com.skilllink.entity.Service;
import com.skilllink.entity.User;
import com.skilllink.enums.Role;
import com.skilllink.repository.ServiceRepository;
import com.skilllink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.skilllink.enums.ProviderApprovalStatus;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/services")
@CrossOrigin(origins = "*")
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;

    public ServiceController(
            ServiceRepository serviceRepository,
            UserRepository userRepository
    ) {
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
    }

    // ==================== ADD SERVICE ====================

    @PostMapping("/add")
    public ResponseEntity<?> addService(
            @RequestParam String email,
            @RequestBody Service service
    ) {

        Optional<User> optionalUser =
                userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        User user = optionalUser.get();

        boolean isProvider = user.getRoles()
                .stream()
                .anyMatch(
                        userRole ->
                                userRole.getRole() == Role.PROVIDER
                );

        if (!isProvider) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body("User is not a service provider");
        }

        service.setProvider(user);

        Service savedService =
                serviceRepository.save(service);

        return ResponseEntity.ok(savedService);
    }

    // ==================== GET PROVIDER SERVICES ====================

    @GetMapping("/provider/{email}")
    public ResponseEntity<?> getProviderServices(
            @PathVariable String email
    ) {

        Optional<User> optionalUser =
                userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        List<Service> services =
                serviceRepository.findByProviderEmail(email);

        return ResponseEntity.ok(services);
    }

    // ==================== UPDATE SERVICE ====================

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateService(
            @PathVariable Long id,
            @RequestParam String email,
            @RequestBody Service updatedService
    ) {

        Optional<Service> optionalService =
                serviceRepository.findById(id);

        if (optionalService.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Service not found");
        }

        Optional<User> optionalUser =
                userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        Service existingService =
                optionalService.get();

        User user =
                optionalUser.get();

        if (existingService.getProvider() == null ||
                !existingService.getProvider()
                        .getId()
                        .equals(user.getId())) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(
                            "You can only edit your own service"
                    );
        }

        existingService.setName(
                updatedService.getName()
        );

        existingService.setCategory(
                updatedService.getCategory()
        );

        existingService.setDescription(
                updatedService.getDescription()
        );

        existingService.setPrice(
                updatedService.getPrice()
        );

        Service savedService =
                serviceRepository.save(existingService);

        return ResponseEntity.ok(savedService);
    }

    // ==================== DELETE SERVICE ====================

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteService(
            @PathVariable Long id,
            @RequestParam String email
    ) {

        Optional<Service> optionalService =
                serviceRepository.findById(id);

        if (optionalService.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Service not found");
        }

        Optional<User> optionalUser =
                userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        Service service =
                optionalService.get();

        User user =
                optionalUser.get();

        if (service.getProvider() == null ||
                !service.getProvider()
                        .getId()
                        .equals(user.getId())) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(
                            "You can only delete your own service"
                    );
        }

        serviceRepository.delete(service);

        return ResponseEntity.ok(
                "Service deleted successfully"
        );
    }

    //----------GetProvider-------//
    @GetMapping("/category/{category}")
    public ResponseEntity<?> getServicesByCategory(
            @PathVariable String category) {

        List<Service> services =
                serviceRepository.findApprovedServicesByCategory(
                        category,
                        ProviderApprovalStatus.APPROVED
                );

        return ResponseEntity.ok(services);
    }
}