package com.skilllink.controller;

import com.skilllink.entity.Notification;
import com.skilllink.entity.ProviderAvailability;
import com.skilllink.entity.ProviderProfile;
import com.skilllink.entity.Service;
import com.skilllink.entity.ServiceRequest;
import com.skilllink.entity.User;
import com.skilllink.enums.ProviderApprovalStatus;
import com.skilllink.repository.NotificationRepository;
import com.skilllink.repository.ProviderAvailabilityRepository;
import com.skilllink.repository.ProviderProfileRepository;
import com.skilllink.repository.ServiceRepository;
import com.skilllink.repository.ServiceRequestRepository;
import com.skilllink.repository.UserRepository;
import com.skilllink.service.FirebaseMessagingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/service-requests")
@CrossOrigin(origins = "*")
public class ServiceRequestController {

    private final ServiceRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final NotificationRepository notificationRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final FirebaseMessagingService firebaseMessagingService;
    private final ProviderAvailabilityRepository availabilityRepository;

    private static final int MAX_PHOTOS = 4;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private static final Path UPLOAD_DIRECTORY =
            Paths.get("uploads", "service-requests")
                    .toAbsolutePath()
                    .normalize();

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public ServiceRequestController(
            ServiceRequestRepository requestRepository,
            UserRepository userRepository,
            ServiceRepository serviceRepository,
            NotificationRepository notificationRepository,
            ProviderProfileRepository providerProfileRepository,
            FirebaseMessagingService firebaseMessagingService,
            ProviderAvailabilityRepository availabilityRepository
    ) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.notificationRepository = notificationRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.firebaseMessagingService = firebaseMessagingService;
        this.availabilityRepository = availabilityRepository;
    }

    // =========================================================
    // CREATE SERVICE REQUEST WITH OPTIONAL PHOTOS
    // =========================================================

    @PostMapping(
            value = "/create",
            consumes = {
                    "multipart/form-data",
                    "application/x-www-form-urlencoded"
            }
    )
    public ResponseEntity<?> createRequest(

            @RequestParam String customerEmail,
            @RequestParam String providerEmail,
            @RequestParam Long serviceId,

            @RequestParam(required = false) String serviceType,
            @RequestParam(required = false) String requestedDate,
            @RequestParam(required = false) String requestedTime,

            @RequestParam(required = false) String address,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,

            @RequestParam String description,

            @RequestParam(required = false) List<MultipartFile> photos

    ) {

        // Validate photos before processing the request.

        if (photos != null) {

            List<MultipartFile> actualPhotos = photos.stream()
                    .filter(photo -> photo != null && !photo.isEmpty())
                    .toList();

            if (actualPhotos.size() > MAX_PHOTOS) {
                return ResponseEntity.badRequest()
                        .body("You can upload a maximum of 4 photos.");
            }

            for (MultipartFile photo : actualPhotos) {

                if (photo.getSize() > MAX_FILE_SIZE) {
                    return ResponseEntity.badRequest()
                            .body("Each photo must be 5MB or smaller.");
                }

                String contentType = photo.getContentType();

                if (contentType == null
                        || !ALLOWED_CONTENT_TYPES.contains(
                        contentType.toLowerCase()
                )) {

                    return ResponseEntity.badRequest()
                            .body("Only JPG, PNG and WEBP photos are allowed.");
                }
            }
        }

        Optional<User> optionalCustomer =
                userRepository.findByEmail(customerEmail);

        if (optionalCustomer.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Customer not found");
        }

        Optional<User> optionalProvider =
                userRepository.findByEmail(providerEmail);

        if (optionalProvider.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Provider not found");
        }

        User customer = optionalCustomer.get();
        User provider = optionalProvider.get();

        if (customer.getId().equals(provider.getId())) {
            return ResponseEntity.badRequest()
                    .body("You cannot request your own service.");
        }

        Optional<ProviderProfile> optionalProfile =
                providerProfileRepository.findByUserId(provider.getId());

        if (optionalProfile.isEmpty()
                || optionalProfile.get().getApprovalStatus()
                != ProviderApprovalStatus.APPROVED) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("This provider is not approved to receive requests.");
        }

        Optional<Service> optionalService =
                serviceRepository.findById(serviceId);

        if (optionalService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Service not found");
        }

        String type = serviceType == null || serviceType.isBlank()
                ? "SCHEDULED"
                : serviceType.trim().toUpperCase();

        if (!type.equals("SCHEDULED") && !type.equals("ON_DEMAND")) {
            return ResponseEntity.badRequest()
                    .body("Service type must be SCHEDULED or ON_DEMAND.");
        }

        if ((latitude == null) != (longitude == null)) {
            return ResponseEntity.badRequest()
                    .body("Provide both latitude and longitude.");
        }

        if (latitude != null
                && (latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180)) {

            return ResponseEntity.badRequest()
                    .body("Invalid GPS coordinates.");
        }

        if ((address == null || address.isBlank()) && latitude == null) {
            return ResponseEntity.badRequest()
                    .body("Provide a service address or GPS location.");
        }

        if (description == null || description.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Please describe the service you need.");
        }

        LocalDate date = null;
        LocalTime time = null;

        if (type.equals("SCHEDULED")) {

            if (requestedDate == null || requestedDate.isBlank()
                    || requestedTime == null || requestedTime.isBlank()) {

                return ResponseEntity.badRequest()
                        .body("Scheduled requests require a preferred date and time.");
            }

            try {
                date = LocalDate.parse(requestedDate);
                time = LocalTime.parse(requestedTime);

            } catch (DateTimeParseException e) {

                return ResponseEntity.badRequest()
                        .body("Invalid date or time. Use yyyy-MM-dd and HH:mm.");
            }

            if (date.isBefore(LocalDate.now())
                    || (date.equals(LocalDate.now())
                    && !time.isAfter(LocalTime.now()))) {

                return ResponseEntity.badRequest()
                        .body("Please select a future date and time.");
            }

            DayOfWeek day = date.getDayOfWeek();

            List<ProviderAvailability> dateSlots =
                    availabilityRepository.findByProviderIdAndSpecificDate(
                            provider.getId(), date
                    );

            List<ProviderAvailability> slots;

            if (!dateSlots.isEmpty()) {

                slots = dateSlots.stream()
                        .filter(ProviderAvailability::isAvailable)
                        .toList();

            } else {

                slots = availabilityRepository
                        .findByProviderIdAndDayOfWeekAndAvailableTrue(
                                provider.getId(), day
                        );
            }

            final LocalTime selectedTime = time;

            boolean withinWorkingHours = slots.stream().anyMatch(slot ->
                    !selectedTime.isBefore(slot.getStartTime())
                            && selectedTime.isBefore(slot.getEndTime())
            );

            if (!withinWorkingHours) {
                return ResponseEntity.badRequest()
                        .body("Provider is not available at the preferred date and time.");
            }

        } else {

            if ((requestedDate != null && !requestedDate.isBlank())
                    || (requestedTime != null && !requestedTime.isBlank())) {

                return ResponseEntity.badRequest()
                        .body("Do not send a preferred date/time for ON_DEMAND requests.");
            }
        }

        // Create service request.

        ServiceRequest request = new ServiceRequest();

        request.setCustomer(customer);
        request.setProvider(provider);
        request.setService(optionalService.get());
        request.setServiceType(type);
        request.setRequestedDate(date);
        request.setRequestedTime(time);
        request.setAddress(address == null ? null : address.trim());
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setDescription(description.trim());
        request.setStatus("PENDING");

        // Save photos.

        List<String> savedPhotoPaths = new ArrayList<>();

        try {

            if (photos != null) {

                for (MultipartFile photo : photos) {

                    if (photo == null || photo.isEmpty()) {
                        continue;
                    }

                    String photoPath = savePhoto(photo);

                    savedPhotoPaths.add(photoPath);
                }
            }

            request.setPhotos(savedPhotoPaths);

            ServiceRequest savedRequest =
                    requestRepository.save(request);

            // Notification to provider.

            createNotification(
                    provider,
                    "New Service Request",
                    customer.getName() + " sent you a "
                            + type.replace("_", " ").toLowerCase()
                            + " request for "
                            + optionalService.get().getName(),
                    "NEW_SERVICE_REQUEST",
                    savedRequest.getId()
            );

            return ResponseEntity.ok(savedRequest);

        } catch (IOException e) {

            // Remove uploaded files if the request fails.

            deletePhotos(savedPhotoPaths);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload photos. Please try again.");

        } catch (Exception e) {

            deletePhotos(savedPhotoPaths);

            System.err.println(
                    "Service request creation failed: " + e.getMessage()
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create service request.");
        }
    }

    // =========================================================
    // SAVE PHOTO
    // =========================================================

    private String savePhoto(MultipartFile photo) throws IOException {

        Files.createDirectories(UPLOAD_DIRECTORY);

        String originalFilename = photo.getOriginalFilename();

        String extension = ".jpg";

        if (originalFilename != null
                && originalFilename.lastIndexOf('.') >= 0) {

            extension = originalFilename.substring(
                    originalFilename.lastIndexOf('.')
            ).toLowerCase();
        }

        String filename = UUID.randomUUID() + extension;

        Path destination = UPLOAD_DIRECTORY.resolve(filename).normalize();

        if (!destination.startsWith(UPLOAD_DIRECTORY)) {
            throw new IOException("Invalid file path.");
        }

        Files.copy(
                photo.getInputStream(),
                destination,
                StandardCopyOption.REPLACE_EXISTING
        );

        return "/uploads/service-requests/" + filename;
    }

    // =========================================================
    // DELETE PHOTOS IF REQUEST CREATION FAILS
    // =========================================================

    private void deletePhotos(List<String> photoPaths) {

        for (String photoPath : photoPaths) {

            try {

                String filename = photoPath.substring(
                        photoPath.lastIndexOf('/') + 1
                );

                Path file = UPLOAD_DIRECTORY.resolve(filename).normalize();

                if (file.startsWith(UPLOAD_DIRECTORY)) {
                    Files.deleteIfExists(file);
                }

            } catch (Exception e) {

                System.err.println(
                        "Failed to delete uploaded photo: " + e.getMessage()
                );
            }
        }
    }

    // =========================================================
    // GET CUSTOMER REQUESTS
    // =========================================================

    @GetMapping("/customer")
    public ResponseEntity<?> getCustomerRequests(
            @RequestParam String email
    ) {

        Optional<User> customer = userRepository.findByEmail(email);

        if (customer.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Customer not found");
        }

        return ResponseEntity.ok(
                requestRepository.findByCustomerEmail(email)
        );
    }

    // =========================================================
    // GET PROVIDER REQUESTS
    // =========================================================

    @GetMapping("/provider")
    public ResponseEntity<?> getProviderRequests(
            @RequestParam String email
    ) {

        Optional<User> provider = userRepository.findByEmail(email);

        if (provider.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Provider not found");
        }

        return ResponseEntity.ok(
                requestRepository.findByProviderEmail(email)
        );
    }

    // =========================================================
    // PROVIDER PROPOSES DATE AND TIME
    // =========================================================

    @PutMapping("/propose-time/{id}")
    public ResponseEntity<?> proposeTime(
            @PathVariable Long id,
            @RequestParam String email,
            @RequestParam String proposedDate,
            @RequestParam String proposedTime
    ) {

        Optional<ServiceRequest> optionalRequest =
                requestRepository.findById(id);

        if (optionalRequest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Service request not found");
        }

        Optional<User> optionalProvider = userRepository.findByEmail(email);

        if (optionalProvider.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Provider not found");
        }

        ServiceRequest request = optionalRequest.get();
        User provider = optionalProvider.get();

        if (request.getProvider() == null
                || !request.getProvider().getId().equals(provider.getId())) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You can only update your own requests.");
        }

        if (!isProviderApproved(provider)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Your provider account is not approved.");
        }

        if (!"PENDING".equals(request.getStatus())
                && !"NEGOTIATING".equals(request.getStatus())) {

            return ResponseEntity.badRequest()
                    .body("Only pending or negotiating requests can receive a time proposal.");
        }

        LocalDate date;
        LocalTime time;

        try {

            date = LocalDate.parse(proposedDate);
            time = LocalTime.parse(proposedTime);

        } catch (DateTimeParseException e) {

            return ResponseEntity.badRequest()
                    .body("Invalid date or time. Use yyyy-MM-dd and HH:mm.");
        }

        if (date.isBefore(LocalDate.now())
                || (date.equals(LocalDate.now())
                && !time.isAfter(LocalTime.now()))) {

            return ResponseEntity.badRequest()
                    .body("Please propose a future date and time.");
        }

        request.setProposedDate(date);
        request.setProposedTime(time);
        request.setStatus("TIME_PROPOSED");

        ServiceRequest savedRequest = requestRepository.save(request);

        createNotification(
                request.getCustomer(),
                "Appointment Time Proposed",
                provider.getName() + " proposed "
                        + date + " at " + time
                        + ". Review the proposed time in SkillLink.",
                "TIME_PROPOSED",
                savedRequest.getId()
        );

        return ResponseEntity.ok(savedRequest);
    }

    // =========================================================
    // CUSTOMER CONFIRMS THE PROPOSED TIME
    // =========================================================

    @PutMapping("/confirm-time/{id}")
    public ResponseEntity<?> confirmTime(
            @PathVariable Long id,
            @RequestParam String email
    ) {

        Optional<ServiceRequest> optionalRequest =
                requestRepository.findById(id);

        if (optionalRequest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Service request not found");
        }

        Optional<User> optionalCustomer = userRepository.findByEmail(email);

        if (optionalCustomer.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Customer not found");
        }

        ServiceRequest request = optionalRequest.get();
        User customer = optionalCustomer.get();

        if (request.getCustomer() == null
                || !request.getCustomer().getId().equals(customer.getId())) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You can only confirm your own requests.");
        }

        if (!"TIME_PROPOSED".equals(request.getStatus())) {
            return ResponseEntity.badRequest()
                    .body("There is no proposed time to confirm.");
        }

        if (request.getProposedDate() == null
                || request.getProposedTime() == null) {

            return ResponseEntity.badRequest()
                    .body("The provider has not proposed a complete appointment time.");
        }

        LocalDate date = request.getProposedDate();
        LocalTime time = request.getProposedTime();

        if (date.isBefore(LocalDate.now())
                || (date.equals(LocalDate.now())
                && !time.isAfter(LocalTime.now()))) {

            return ResponseEntity.badRequest()
                    .body("The proposed time has passed. Please ask the provider for a new time.");
        }

        boolean alreadyBooked =
                requestRepository
                        .existsByProviderIdAndRequestedDateAndRequestedTimeAndStatusIn(
                                request.getProvider().getId(),
                                date,
                                time,
                                List.of("CONFIRMED", "ACCEPTED", "IN_PROGRESS")
                        );

        if (alreadyBooked) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("This time is no longer available. Please ask the provider for another time.");
        }

        request.setRequestedDate(date);
        request.setRequestedTime(time);
        request.setStatus("CONFIRMED");

        ServiceRequest savedRequest = requestRepository.save(request);

        createNotification(
                request.getProvider(),
                "Appointment Confirmed",
                customer.getName() + " confirmed your proposed appointment.",
                "APPOINTMENT_CONFIRMED",
                savedRequest.getId()
        );

        createNotification(
                customer,
                "Booking Confirmed",
                "Your appointment time has been confirmed.",
                "BOOKING_CONFIRMED",
                savedRequest.getId()
        );

        return ResponseEntity.ok(savedRequest);
    }

    // =========================================================
    // UPDATE REQUEST STATUS
    // =========================================================

    @PutMapping("/update-status/{id}")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestParam String email,
            @RequestParam String status
    ) {

        Optional<ServiceRequest> optionalRequest =
                requestRepository.findById(id);

        if (optionalRequest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Service request not found");
        }

        Optional<User> optionalProvider = userRepository.findByEmail(email);

        if (optionalProvider.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Provider not found");
        }

        ServiceRequest request = optionalRequest.get();
        User provider = optionalProvider.get();

        if (request.getProvider() == null
                || !request.getProvider().getId().equals(provider.getId())) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You can only update your own requests.");
        }

        if (!status.equals("ACCEPTED")
                && !status.equals("REJECTED")
                && !status.equals("COMPLETED")
                && !status.equals("IN_PROGRESS")) {

            return ResponseEntity.badRequest()
                    .body("Invalid status.");
        }

        if (status.equals("ACCEPTED")) {

            if (!isProviderApproved(provider)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Your provider account is not approved yet.");
            }

            if ("SCHEDULED".equals(request.getServiceType())
                    || "ON_DEMAND".equals(request.getServiceType())) {

                if (!"PENDING".equals(request.getStatus())
                        && !"NEGOTIATING".equals(request.getStatus())) {

                    return ResponseEntity.badRequest()
                            .body("This request cannot be accepted in its current status.");
                }

                return ResponseEntity.badRequest()
                        .body("Please propose an appointment time first using /propose-time/{id}.");
            }
        }

        if (status.equals("REJECTED")
                && !"PENDING".equals(request.getStatus())
                && !"NEGOTIATING".equals(request.getStatus())) {

            return ResponseEntity.badRequest()
                    .body("Only pending or negotiating requests can be rejected.");
        }

        if (status.equals("IN_PROGRESS")
                && !"CONFIRMED".equals(request.getStatus())
                && !"ACCEPTED".equals(request.getStatus())) {

            return ResponseEntity.badRequest()
                    .body("Only confirmed bookings can be started.");
        }

        if (status.equals("COMPLETED")
                && !"IN_PROGRESS".equals(request.getStatus())
                && !"ACCEPTED".equals(request.getStatus())) {

            return ResponseEntity.badRequest()
                    .body("Only started bookings can be completed.");
        }

        request.setStatus(status);

        ServiceRequest updatedRequest = requestRepository.save(request);

        User customer = request.getCustomer();

        String title;
        String message;
        String notificationType;

        switch (status) {

            case "REJECTED" -> {
                title = "Request Rejected";
                message = "Your service request has been rejected by "
                        + provider.getName();
                notificationType = "REQUEST_REJECTED";
            }

            case "IN_PROGRESS" -> {
                title = "Service Started";
                message = provider.getName() + " has started your service.";
                notificationType = "SERVICE_STARTED";
            }

            case "COMPLETED" -> {
                title = "Service Completed";
                message = provider.getName()
                        + " marked your service as completed.";
                notificationType = "SERVICE_COMPLETED";
            }

            default -> {
                title = "Request Updated";
                message = "Your service request status is now " + status + ".";
                notificationType = "REQUEST_UPDATED";
            }
        }

        createNotification(
                customer,
                title,
                message,
                notificationType,
                updatedRequest.getId()
        );

        return ResponseEntity.ok(updatedRequest);
    }

    // =========================================================
    // PROVIDER BOOKINGS
    // =========================================================

    @GetMapping("/provider/bookings")
    public ResponseEntity<?> getProviderBookings(
            @RequestParam String email
    ) {

        Optional<User> provider = userRepository.findByEmail(email);

        if (provider.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Provider not found");
        }

        List<ServiceRequest> bookings =
                requestRepository.findByProviderEmail(email)
                        .stream()
                        .filter(request ->
                                "CONFIRMED".equals(request.getStatus())
                                        || "ACCEPTED".equals(request.getStatus())
                                        || "IN_PROGRESS".equals(request.getStatus())
                                        || "COMPLETED".equals(request.getStatus())
                        )
                        .toList();

        return ResponseEntity.ok(bookings);
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    private boolean isProviderApproved(User provider) {

        Optional<ProviderProfile> profile =
                providerProfileRepository.findByUserId(provider.getId());

        return profile.isPresent()
                && profile.get().getApprovalStatus()
                == ProviderApprovalStatus.APPROVED;
    }

    private void createNotification(
            User user,
            String title,
            String message,
            String type,
            Long referenceId
    ) {

        Notification notification = new Notification();

        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);

        String token = user.getFcmToken();

        if (token != null && !token.isBlank()) {

            try {

                firebaseMessagingService.sendPushNotification(
                        token, title, message
                );

            } catch (Exception e) {

                System.err.println(
                        "Push notification failed: " + e.getMessage()
                );
            }
        }
    }
}