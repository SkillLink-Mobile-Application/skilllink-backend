package com.skilllink.controller;

import com.skilllink.entity.ProviderProfile;
import com.skilllink.entity.User;
import com.skilllink.enums.Role;
import com.skilllink.repository.ProviderProfileRepository;
import com.skilllink.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/provider-profile")
@CrossOrigin(origins = "*")
public class ProviderProfileController {

    private final ProviderProfileRepository profileRepository;
    private final UserRepository userRepository;

    public ProviderProfileController(
            ProviderProfileRepository profileRepository,
            UserRepository userRepository
    ) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    // ================= SAVE / UPDATE PROFILE =================

    @PostMapping("/save")
    public ResponseEntity<?> saveProfile(
            @RequestParam String email,
            @RequestBody ProviderProfile profile
    ) {
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        User user = optionalUser.get();

        boolean isProvider = user.getRoles()
                .stream()
                .anyMatch(userRole -> userRole.getRole() == Role.PROVIDER);

        if (!isProvider) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("User is not a service provider");
        }

        // Required verification details
        if (profile.getNicNumber() == null
                || profile.getNicNumber().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("NIC number is required");
        }

        if (profile.getServiceCategory() == null
                || profile.getServiceCategory().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Service category is required");
        }

        if (!profile.isTermsAccepted()) {
            return ResponseEntity.badRequest()
                    .body("Please accept the terms and conditions");
        }

        ProviderProfile providerProfile =
                profileRepository.findByUserEmail(email).orElse(null);

        boolean isNewProfile = providerProfile == null;

        if (isNewProfile) {
            providerProfile = new ProviderProfile();
            providerProfile.setUser(user);
            providerProfile.setApprovalStatus(
                    com.skilllink.enums.ProviderApprovalStatus.PENDING
            );
            providerProfile.setBadge(
                    com.skilllink.enums.ProviderBadge.NONE
            );
        }

        providerProfile.setLocation(profile.getLocation());
        providerProfile.setExperience(profile.getExperience());
        providerProfile.setDescription(profile.getDescription());
        providerProfile.setNicNumber(profile.getNicNumber().trim());
        providerProfile.setServiceCategory(profile.getServiceCategory().trim());
        providerProfile.setCertificateName(profile.getCertificateName());
        providerProfile.setIssuingInstitute(profile.getIssuingInstitute());
        providerProfile.setTermsAccepted(true);

        if (profile.getUser() != null) {
            if (profile.getUser().getName() != null
                    && !profile.getUser().getName().trim().isEmpty()) {
                user.setName(profile.getUser().getName().trim());
            }

            if (profile.getUser().getPhone() != null
                    && !profile.getUser().getPhone().trim().isEmpty()) {
                user.setPhone(profile.getUser().getPhone().trim());
            }

            userRepository.save(user);
        }

        if (profile.getProfileImage() != null
                && !profile.getProfileImage().isEmpty()) {
            providerProfile.setProfileImage(profile.getProfileImage());
        }

        ProviderProfile savedProfile =
                profileRepository.save(providerProfile);

        return ResponseEntity.ok(savedProfile);
    }

    // ================= GET PROFILE =================

    @GetMapping("/{email}")
    public ResponseEntity<?> getProfile(@PathVariable String email) {
        Optional<ProviderProfile> profile =
                profileRepository.findByUserEmail(email);

        if (profile.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Provider profile not found");
        }

        return ResponseEntity.ok(profile.get());
    }

    // ================= UPLOAD PROVIDER PROFILE IMAGE =================

    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadProfileImage(
            @RequestParam String email,
            @RequestParam("image") MultipartFile image
    ) {
        try {
            Optional<User> optionalUser = userRepository.findByEmail(email);

            if (optionalUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("User not found");
            }

            User user = optionalUser.get();

            boolean isProvider = user.getRoles()
                    .stream()
                    .anyMatch(userRole -> userRole.getRole() == Role.PROVIDER);

            if (!isProvider) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("User is not a service provider");
            }

            if (image == null || image.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Please select an image");
            }

            String originalName = image.getOriginalFilename();

            if (originalName == null
                    || !originalName.toLowerCase().matches(".*\\.(jpg|jpeg|png)$")) {
                return ResponseEntity.badRequest()
                        .body("Only JPG, JPEG and PNG images are allowed");
            }

            Path uploadPath = Paths.get("uploads", "profile-images");
            Files.createDirectories(uploadPath);

            String extension = originalName.substring(originalName.lastIndexOf("."));
            String fileName = "profile_" + UUID.randomUUID() + extension;

            Path filePath = uploadPath.resolve(fileName);
            Files.write(filePath, image.getBytes());

            ProviderProfile providerProfile =
                    profileRepository.findByUserEmail(email).orElseGet(() -> {
                        ProviderProfile newProfile = new ProviderProfile();
                        newProfile.setUser(user);
                        return newProfile;
                    });

            String imagePath = "/uploads/profile-images/" + fileName;
            providerProfile.setProfileImage(imagePath);

            profileRepository.save(providerProfile);

            return ResponseEntity.ok(imagePath);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload image: " + e.getMessage());
        }
    }

    // ================= CUSTOMER PROFILE IMAGE UPLOAD =================

    @PostMapping("/customer-upload-image")
    public ResponseEntity<?> uploadCustomerProfileImage(
            @RequestParam String email,
            @RequestParam("image") MultipartFile image
    ) {
        try {
            Optional<User> optionalUser = userRepository.findByEmail(email);

            if (optionalUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("User not found");
            }

            User user = optionalUser.get();

            if (image == null || image.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Please select an image");
            }

            String originalName = image.getOriginalFilename();

            if (originalName == null
                    || !originalName.toLowerCase().matches(".*\\.(jpg|jpeg|png)$")) {
                return ResponseEntity.badRequest()
                        .body("Only JPG, JPEG and PNG images are allowed");
            }

            Path uploadPath = Paths.get("uploads", "profile-images");
            Files.createDirectories(uploadPath);

            String extension = originalName.substring(originalName.lastIndexOf("."));
            String fileName = "customer_profile_" + UUID.randomUUID() + extension;

            Path filePath = uploadPath.resolve(fileName);
            Files.write(filePath, image.getBytes());

            String imagePath = "/uploads/profile-images/" + fileName;

            user.setProfileImage(imagePath);
            userRepository.save(user);

            return ResponseEntity.ok(imagePath);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload image: " + e.getMessage());
        }
    }

    // ================= UPLOAD PROVIDER CERTIFICATE =================

    @PostMapping("/upload-certificate")
    public ResponseEntity<?> uploadCertificate(
            @RequestParam("email") String email,
            @RequestParam("certificate") MultipartFile certificate
    ) {
        try {
            Optional<User> optionalUser = userRepository.findByEmail(email);

            if (optionalUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("User not found");
            }

            User user = optionalUser.get();

            boolean isProvider = user.getRoles()
                    .stream()
                    .anyMatch(userRole -> userRole.getRole() == Role.PROVIDER);

            if (!isProvider) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("User is not a service provider");
            }

            if (certificate == null || certificate.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Please select a certificate file");
            }

            if (certificate.getSize() > 10L * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body("Certificate file must be 10 MB or smaller");
            }

            String originalName = certificate.getOriginalFilename();

            if (originalName == null || !originalName.contains(".")) {
                return ResponseEntity.badRequest()
                        .body("Invalid certificate file");
            }

            String extension = originalName
                    .substring(originalName.lastIndexOf("."))
                    .toLowerCase();

            if (!extension.matches("\\.(pdf|jpg|jpeg|png)")) {
                return ResponseEntity.badRequest()
                        .body("Only PDF, JPG, JPEG and PNG files are allowed");
            }

            ProviderProfile profile = profileRepository
                    .findByUserEmail(email)
                    .orElse(null);

            if (profile == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Provider profile not found. Save your profile first.");
            }

            Path uploadDirectory = Paths.get("uploads", "certificates")
                    .toAbsolutePath()
                    .normalize();

            Files.createDirectories(uploadDirectory);

            String fileName = UUID.randomUUID() + extension;
            Path targetPath = uploadDirectory.resolve(fileName).normalize();

            if (!targetPath.startsWith(uploadDirectory)) {
                return ResponseEntity.badRequest()
                        .body("Invalid file path");
            }

            certificate.transferTo(targetPath);

            profile.setCertificateFile("/uploads/certificates/" + fileName);
            profileRepository.save(profile);

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Certificate uploaded successfully",
                            "certificateFile", profile.getCertificateFile()
                    )
            );

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload certificate");
        }
    }
}