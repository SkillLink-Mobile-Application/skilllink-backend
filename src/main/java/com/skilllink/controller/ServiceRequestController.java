
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
                        contentType.toLowerCase())) {
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

        if (!isProviderApproved(provider)) {
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

            if (!isFutureDateTime(date, time)) {
                return ResponseEntity.badRequest()
                        .body("Please select a future date and time.");
            }

            boolean alreadyBooked =
                    requestRepository
                            .existsByProviderIdAndRequestedDateAndRequestedTimeAndStatusIn(
                                    provider.getId(),
                                    date,
                                    time,
                                    List.of("CONFIRMED", "ACCEPTED", "IN_PROGRESS")
                            );

            if (alreadyBooked) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("This time is already booked. Please select another time.");
            }

        } else {

            if ((requestedDate != null && !requestedDate.isBlank())
                    || (requestedTime != null && !requestedTime.isBlank())) {
                return ResponseEntity.badRequest()
                        .body("Do not send a preferred date/time for ON_DEMAND requests.");
            }
        }

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

            ServiceRequest savedRequest = requestRepository.save(request);

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
    // PROVIDER PROPOSES / RESCHEDULES DATE AND TIME
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

        String currentStatus = request.getStatus();

        if (!"PENDING".equals(currentStatus)
                && !"NEGOTIATING".equals(currentStatus)) {
            return ResponseEntity.badRequest()
                    .body("This request cannot receive a time proposal.");
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

        if (!isFutureDateTime(date, time)) {
            return ResponseEntity.badRequest()
                    .body("Please propose a future date and time.");
        }

        // Check only whether this exact time is already booked.
        // Provider can propose a time outside normal availability slots.
        boolean alreadyBooked =
                requestRepository
                        .existsByProviderIdAndRequestedDateAndRequestedTimeAndStatusIn(
                                provider.getId(),
                                date,
                                time,
                                List.of("CONFIRMED", "ACCEPTED", "IN_PROGRESS")
                        );

        if (alreadyBooked) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("This time is already booked. Please select another time.");
        }

        boolean isReschedule = "SCHEDULED".equals(request.getServiceType());

        request.setProposedDate(date);
        request.setProposedTime(time);

        if (isReschedule) {
            request.setStatus("RESCHEDULE_PROPOSED");
        } else {
            request.setStatus("TIME_PROPOSED");
        }

        ServiceRequest savedRequest = requestRepository.save(request);

        String title = isReschedule
                ? "New Appointment Time Proposed"
                : "Appointment Time Proposed";

        String message = provider.getName()
                + " proposed " + date + " at " + time
                + ". Please review and confirm or decline this time.";

        createNotification(
                request.getCustomer(),
                title,
                message,
                isReschedule ? "RESCHEDULE_PROPOSED" : "TIME_PROPOSED",
                savedRequest.getId()
        );

        return ResponseEntity.ok(savedRequest);
    }

    // =========================================================
    // CUSTOMER CONFIRMS PROPOSED TIME
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

        String currentStatus = request.getStatus();

        if (!"TIME_PROPOSED".equals(currentStatus)
                && !"RESCHEDULE_PROPOSED".equals(currentStatus)) {
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

        if (!isFutureDateTime(date, time)) {
            return ResponseEntity.badRequest()
                    .body("The proposed time has passed. Please ask the provider for a new time.");
        }

        // Recheck booking conflict when customer confirms.
        // Do not check normal provider availability slots here.
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
        request.setProposedDate(null);
        request.setProposedTime(null);
        request.setStatus("CONFIRMED");

        ServiceRequest savedRequest = requestRepository.save(request);

        createNotification(
                request.getProvider(),
                "Appointment Confirmed",
                customer.getName() + " confirmed the proposed appointment.",
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
    // CUSTOMER DECLINES PROPOSED TIME
    // =========================================================

    @PutMapping("/decline-time/{id}")
    public ResponseEntity<?> declineTime(
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
                    .body("You can only update your own requests.");
        }

        if (!"TIME_PROPOSED".equals(request.getStatus())
                && !"RESCHEDULE_PROPOSED".equals(request.getStatus())) {
            return ResponseEntity.badRequest()
                    .body("There is no proposed time to decline.");
        }

        request.setProposedDate(null);
        request.setProposedTime(null);
        request.setStatus("NEGOTIATING");

        ServiceRequest savedRequest = requestRepository.save(request);

        createNotification(
                request.getProvider(),
                "Proposed Time Declined",
                customer.getName()
                        + " declined your proposed time. You can propose another time.",
                "TIME_DECLINED",
                savedRequest.getId()
        );

        return ResponseEntity.ok(savedRequest);
    }

    // =========================================================
// CUSTOMER PROPOSES A NEW DATE AND TIME
// =========================================================

    @PutMapping("/customer-propose-time/{id}")
    public ResponseEntity<?> customerProposeTime(
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
                    .body("You can only update your own requests.");
        }

        if (!"NEGOTIATING".equals(request.getStatus())) {
            return ResponseEntity.badRequest()
                    .body("You can only propose a time while negotiating.");
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

        if (!isFutureDateTime(date, time)) {
            return ResponseEntity.badRequest()
                    .body("Please select a future date and time.");
        }

        request.setProposedDate(date);
        request.setProposedTime(time);
        request.setStatus("CUSTOMER_TIME_PROPOSED");

        ServiceRequest savedRequest = requestRepository.save(request);

        createNotification(
                request.getProvider(),
                "Customer Proposed a New Time",
                customer.getName() + " proposed " + date + " at " + time
                        + ". Please review the proposed appointment time.",
                "CUSTOMER_TIME_PROPOSED",
                savedRequest.getId()
        );

        return ResponseEntity.ok(savedRequest);
    }

    // =========================================================
// PROVIDER ACCEPTS CUSTOMER-PROPOSED DATE AND TIME
// =========================================================

    @PutMapping("/accept-customer-time/{id}")
    public ResponseEntity<?> acceptCustomerTime(
            @PathVariable Long id,
            @RequestParam String email
    ) {

        Optional<ServiceRequest> optionalRequest =
                requestRepository.findById(id);

        if (optionalRequest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Service request not found.");
        }

        Optional<User> optionalProvider = userRepository.findByEmail(email);

        if (optionalProvider.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Provider not found.");
        }

        ServiceRequest request = optionalRequest.get();
        User provider = optionalProvider.get();

        // Verify request ownership
        if (request.getProvider() == null
                || !request.getProvider().getId().equals(provider.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You can only update your own requests.");
        }

        // Verify provider approval
        if (!isProviderApproved(provider)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Your provider account is not approved.");
        }

        // Check request status
        if (!"CUSTOMER_TIME_PROPOSED".equals(request.getStatus())) {
            return ResponseEntity.badRequest()
                    .body("There is no customer-proposed time to accept.");
        }

        // Check proposed date and time
        LocalDate date = request.getProposedDate();
        LocalTime time = request.getProposedTime();

        if (date == null || time == null) {
            return ResponseEntity.badRequest()
                    .body("The customer's proposed date and time are missing.");
        }

        if (!isFutureDateTime(date, time)) {
            return ResponseEntity.badRequest()
                    .body("The proposed appointment time has passed.");
        }

        // Check whether another booking already occupies this time
        boolean alreadyBooked =
                requestRepository
                        .existsByProviderIdAndRequestedDateAndRequestedTimeAndStatusIn(
                                provider.getId(),
                                date,
                                time,
                                List.of("CONFIRMED", "ACCEPTED", "IN_PROGRESS")
                        );

        if (alreadyBooked) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("This time is already booked. Please ask the customer for another time.");
        }

        // Confirm the customer's proposed appointment
        request.setRequestedDate(date);
        request.setRequestedTime(time);

        request.setProposedDate(null);
        request.setProposedTime(null);

        request.setStatus("CONFIRMED");

        ServiceRequest savedRequest = requestRepository.save(request);

        // Notify customer
        createNotification(
                request.getCustomer(),
                "Appointment Time Accepted",
                provider.getName()
                        + " accepted your proposed appointment on "
                        + date + " at " + time + ".",
                "CUSTOMER_TIME_ACCEPTED",
                savedRequest.getId()
        );

        return ResponseEntity.ok(savedRequest);
    }

    // =========================================================
// PROVIDER DECLINES CUSTOMER-PROPOSED DATE AND TIME
// =========================================================

    @PutMapping("/decline-customer-time/{id}")
    public ResponseEntity<?> declineCustomerTime(
            @PathVariable Long id,
            @RequestParam String email
    ) {

        Optional<ServiceRequest> optionalRequest =
                requestRepository.findById(id);

        if (optionalRequest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Service request not found.");
        }

        Optional<User> optionalProvider = userRepository.findByEmail(email);

        if (optionalProvider.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Provider not found.");
        }

        ServiceRequest request = optionalRequest.get();
        User provider = optionalProvider.get();

        // Verify request ownership
        if (request.getProvider() == null
                || !request.getProvider().getId().equals(provider.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You can only update your own requests.");
        }

        // Verify provider approval
        if (!isProviderApproved(provider)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Your provider account is not approved.");
        }

        // Check request status
        if (!"CUSTOMER_TIME_PROPOSED".equals(request.getStatus())) {
            return ResponseEntity.badRequest()
                    .body("There is no customer-proposed time to decline.");
        }

        // Clear proposed appointment
        request.setProposedDate(null);
        request.setProposedTime(null);

        // Continue negotiation
        request.setStatus("NEGOTIATING");

        ServiceRequest savedRequest = requestRepository.save(request);

        // Notify customer
        createNotification(
                request.getCustomer(),
                "Proposed Time Declined",
                provider.getName()
                        + " declined your proposed appointment time. "
                        + "You can discuss another suitable time.",
                "CUSTOMER_TIME_DECLINED",
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
            @RequestParam String status,
            @RequestParam(required = false) String reason
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

        String newStatus = status.trim().toUpperCase();

        if (!newStatus.equals("ACCEPTED")
                && !newStatus.equals("REJECTED")
                && !newStatus.equals("COMPLETED")
                && !newStatus.equals("IN_PROGRESS")) {
            return ResponseEntity.badRequest()
                    .body("Invalid status.");
        }

        if (!isProviderApproved(provider)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Your provider account is not approved.");
        }

        // ACCEPT SCHEDULED REQUEST DIRECTLY
        if (newStatus.equals("ACCEPTED")) {

            if (!"SCHEDULED".equals(request.getServiceType())) {
                return ResponseEntity.badRequest()
                        .body("For ON_DEMAND requests, use /propose-time/{id} to set an appointment time.");
            }

            if (!"PENDING".equals(request.getStatus())
                    && !"NEGOTIATING".equals(request.getStatus())) {
                return ResponseEntity.badRequest()
                        .body("This request cannot be accepted in its current status.");
            }

            LocalDate date = request.getRequestedDate();
            LocalTime time = request.getRequestedTime();

            if (date == null || time == null) {
                return ResponseEntity.badRequest()
                        .body("The customer's requested date and time are missing.");
            }

            if (!isFutureDateTime(date, time)) {
                return ResponseEntity.badRequest()
                        .body("The requested appointment time has passed.");
            }

            if (!isProviderAvailable(provider.getId(), date, time)) {
                return ResponseEntity.badRequest()
                        .body("Provider is not available at the requested date and time.");
            }

            boolean alreadyBooked =
                    requestRepository
                            .existsByProviderIdAndRequestedDateAndRequestedTimeAndStatusIn(
                                    provider.getId(),
                                    date,
                                    time,
                                    List.of("CONFIRMED", "ACCEPTED", "IN_PROGRESS")
                            );

            if (alreadyBooked) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("This appointment time is already booked.");
            }

            request.setStatus("ACCEPTED");
            request.setRejectionReason(null);

        } else if (newStatus.equals("REJECTED")) {

            if (!"PENDING".equals(request.getStatus())
                    && !"NEGOTIATING".equals(request.getStatus())) {
                return ResponseEntity.badRequest()
                        .body("Only pending or negotiating requests can be rejected.");
            }

            if (reason == null || reason.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("A rejection reason is required.");
            }

            request.setRejectionReason(reason.trim());
            request.setProposedDate(null);
            request.setProposedTime(null);
            request.setStatus("REJECTED");

        } else if (newStatus.equals("IN_PROGRESS")) {

            if (!"CONFIRMED".equals(request.getStatus())
                    && !"ACCEPTED".equals(request.getStatus())) {
                return ResponseEntity.badRequest()
                        .body("Only confirmed or accepted bookings can be started.");
            }

            request.setStatus("IN_PROGRESS");

        } else if (newStatus.equals("COMPLETED")) {

            if (!"IN_PROGRESS".equals(request.getStatus())
                    && !"ACCEPTED".equals(request.getStatus())) {
                return ResponseEntity.badRequest()
                        .body("Only started bookings can be completed.");
            }

            request.setStatus("COMPLETED");
        }

        ServiceRequest updatedRequest = requestRepository.save(request);

        User customer = request.getCustomer();

        String title;
        String message;
        String notificationType;

        switch (newStatus) {

            case "ACCEPTED" -> {
                title = "Request Accepted";
                message = provider.getName()
                        + " accepted your requested appointment date and time.";
                notificationType = "REQUEST_ACCEPTED";
            }

            case "REJECTED" -> {
                title = "Request Rejected";
                message = provider.getName()
                        + " rejected your service request. Reason: "
                        + request.getRejectionReason();
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
                message = "Your service request status is now " + newStatus + ".";
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
    // CHECK PROVIDER AVAILABILITY
    // =========================================================

    private boolean isProviderAvailable(
            Long providerId,
            LocalDate date,
            LocalTime time
    ) {

        DayOfWeek day = date.getDayOfWeek();

        List<ProviderAvailability> dateSlots =
                availabilityRepository.findByProviderIdAndSpecificDate(
                        providerId, date
                );

        List<ProviderAvailability> slots;

        if (!dateSlots.isEmpty()) {
            slots = dateSlots.stream()
                    .filter(ProviderAvailability::isAvailable)
                    .toList();
        } else {
            slots = availabilityRepository
                    .findByProviderIdAndDayOfWeekAndAvailableTrue(
                            providerId, day
                    );
        }

        return slots.stream().anyMatch(slot ->
                !time.isBefore(slot.getStartTime())
                        && time.isBefore(slot.getEndTime())
        );
    }

    // =========================================================
    // CHECK FUTURE DATE AND TIME
    // =========================================================

    private boolean isFutureDateTime(LocalDate date, LocalTime time) {

        if (date == null || time == null) {
            return false;
        }

        return date.isAfter(LocalDate.now())
                || (date.equals(LocalDate.now())
                && time.isAfter(LocalTime.now()));
    }

    // =========================================================
    // CHECK PROVIDER APPROVAL
    // =========================================================

    private boolean isProviderApproved(User provider) {

        Optional<ProviderProfile> profile =
                providerProfileRepository.findByUserId(provider.getId());

        return profile.isPresent()
                && profile.get().getApprovalStatus()
                == ProviderApprovalStatus.APPROVED;
    }

    // =========================================================
    // CREATE NOTIFICATION
    // =========================================================

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
