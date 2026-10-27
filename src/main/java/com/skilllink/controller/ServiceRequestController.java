
package com.skilllink.controller;

import com.skilllink.entity.Notification;
import com.skilllink.entity.Service;
import com.skilllink.entity.ServiceRequest;
import com.skilllink.entity.User;
import com.skilllink.entity.ProviderProfile;
import com.skilllink.enums.ProviderApprovalStatus;
import com.skilllink.repository.NotificationRepository;
import com.skilllink.repository.ServiceRepository;
import com.skilllink.repository.ServiceRequestRepository;
import com.skilllink.repository.UserRepository;
import com.skilllink.repository.ProviderProfileRepository;
import com.skilllink.service.FirebaseMessagingService;
import com.skilllink.repository.ProviderAvailabilityRepository;
import java.time.DayOfWeek;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

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

    // ==================== CREATE SERVICE REQUEST ====================


    // ==================== CREATE SERVICE REQUEST ====================

    @PostMapping("/create")
    public ResponseEntity<?> createRequest(
            @RequestParam String customerEmail,
            @RequestParam String providerEmail,
            @RequestParam Long serviceId,
            @RequestParam String requestedDate,
            @RequestParam String requestedTime,
            @RequestParam String address,
            @RequestParam String description
    ) {
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

        // Check provider approval
        Optional<ProviderProfile> optionalProfile =
                providerProfileRepository.findByUserId(provider.getId());

        if (optionalProfile.isEmpty()
                || optionalProfile.get().getApprovalStatus()
                != ProviderApprovalStatus.APPROVED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("This provider is not approved to receive requests.");
        }

        // Check service
        Optional<Service> optionalService =
                serviceRepository.findById(serviceId);

        if (optionalService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Service not found");
        }

        // Parse requested date and time
        LocalDate date;
        LocalTime time;

        try {
            date = LocalDate.parse(requestedDate);
            time = LocalTime.parse(requestedTime);
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body("Invalid date or time format. Use yyyy-MM-dd and HH:mm.");
        }

        // Reject past bookings
        if (date.isBefore(LocalDate.now())
                || (date.equals(LocalDate.now())
                && !time.isAfter(LocalTime.now()))) {
            return ResponseEntity.badRequest()
                    .body("Please select a future date and time.");
        }

        // Check provider availability for selected day
        DayOfWeek day = date.getDayOfWeek();

        List<com.skilllink.entity.ProviderAvailability> slots =
                availabilityRepository
                        .findByProviderIdAndDayOfWeekAndAvailableTrue(
                                provider.getId(), day);

        boolean withinWorkingHours = slots.stream().anyMatch(slot ->
                !time.isBefore(slot.getStartTime())
                        && time.isBefore(slot.getEndTime())
        );

        if (!withinWorkingHours) {
            return ResponseEntity.badRequest()
                    .body("Provider is not available at the selected date and time.");
        }

        // Prevent duplicate bookings at the same date and time
        boolean alreadyBooked =
                requestRepository
                        .existsByProviderIdAndRequestedDateAndRequestedTimeAndStatusIn(
                                provider.getId(),
                                date,
                                time,
                                List.of("PENDING", "ACCEPTED")
                        );

        if (alreadyBooked) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("This time slot is already booked. Please select another time.");
        }

        // Create service request
        Service service = optionalService.get();

        ServiceRequest request = new ServiceRequest();
        request.setCustomer(customer);
        request.setProvider(provider);
        request.setService(service);
        request.setRequestedDate(date);
        request.setRequestedTime(time);
        request.setAddress(address);
        request.setDescription(description);
        request.setStatus("PENDING");

        ServiceRequest savedRequest = requestRepository.save(request);

        // Database notification
        Notification notification = new Notification();
        notification.setUser(provider);
        notification.setTitle("New Service Request");
        notification.setMessage(
                customer.getName()
                        + " sent you a service request for "
                        + service.getName()
        );
        notification.setType("NEW_SERVICE_REQUEST");
        notification.setReferenceId(savedRequest.getId());
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);

        // Push notification
        String providerFcmToken = provider.getFcmToken();

        if (providerFcmToken != null && !providerFcmToken.isBlank()) {
            try {
                firebaseMessagingService.sendPushNotification(
                        providerFcmToken,
                        notification.getTitle(),
                        notification.getMessage()
                );
            } catch (Exception e) {
                System.err.println(
                        "Provider push notification failed: "
                                + e.getMessage()
                );
            }
        }

        return ResponseEntity.ok(savedRequest);
    }

    // ==================== GET CUSTOMER REQUESTS ====================

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

    // ==================== GET PROVIDER REQUESTS ====================

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

    // ==================== UPDATE REQUEST STATUS ====================

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

        Optional<User> optionalProvider =
                userRepository.findByEmail(email);

        if (optionalProvider.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Provider not found");
        }

        ServiceRequest request = optionalRequest.get();
        User provider = optionalProvider.get();

        // SECURITY CHECK: PROVIDER CAN UPDATE ONLY THEIR OWN REQUESTS

        if (request.getProvider() == null
                || !request.getProvider().getId().equals(provider.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You can only update your own requests");
        }

        // VALID STATUS CHECK

        if (!"ACCEPTED".equals(status)
                && !"REJECTED".equals(status)
                && !"COMPLETED".equals(status)) {
            return ResponseEntity.badRequest()
                    .body("Invalid status");
        }

        // PROVIDER APPROVAL CHECK BEFORE ACCEPTING A REQUEST

        if ("ACCEPTED".equals(status)) {

            Optional<ProviderProfile> optionalProfile =
                    providerProfileRepository.findByUserId(provider.getId());

            if (optionalProfile.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Provider profile not found. You cannot accept requests.");
            }

            ProviderProfile profile = optionalProfile.get();

            if (profile.getApprovalStatus() != ProviderApprovalStatus.APPROVED) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Your provider account is not approved yet. You cannot accept service requests.");
            }

            // ONLY PENDING REQUESTS CAN BE ACCEPTED

            if (!"PENDING".equals(request.getStatus())) {
                return ResponseEntity.badRequest()
                        .body("Only pending requests can be accepted.");
            }
        }

        // PREVENT INVALID STATUS TRANSITIONS

        if ("COMPLETED".equals(status)
                && !"ACCEPTED".equals(request.getStatus())) {
            return ResponseEntity.badRequest()
                    .body("Only accepted requests can be completed.");
        }

        if ("REJECTED".equals(status)
                && !"PENDING".equals(request.getStatus())) {
            return ResponseEntity.badRequest()
                    .body("Only pending requests can be rejected.");
        }

        // UPDATE STATUS

        request.setStatus(status);

        ServiceRequest updatedRequest =
                requestRepository.save(request);

        // DATABASE NOTIFICATION FOR CUSTOMER

        User customer = request.getCustomer();

        if (customer == null) {
            System.err.println(
                    "Customer is missing for request ID: " + id
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Customer not found for this request");
        }

        Notification notification = new Notification();
        notification.setUser(customer);

        if ("ACCEPTED".equals(status)) {
            notification.setTitle("Request Accepted");
            notification.setMessage(
                    "Your service request has been accepted by "
                            + provider.getName()
            );
            notification.setType("REQUEST_ACCEPTED");

        } else if ("REJECTED".equals(status)) {
            notification.setTitle("Request Rejected");
            notification.setMessage(
                    "Your service request has been rejected by "
                            + provider.getName()
            );
            notification.setType("REQUEST_REJECTED");

        } else {
            notification.setTitle("Service Completed");
            notification.setMessage(
                    "Your service request has been marked as completed by "
                            + provider.getName()
            );
            notification.setType("SERVICE_COMPLETED");
        }

        notification.setReferenceId(request.getId());
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);

        // PUSH NOTIFICATION TO CUSTOMER ONLY

        String customerFcmToken = customer.getFcmToken();

        System.out.println("\n========== CUSTOMER FCM DEBUG ==========");
        System.out.println("REQUEST ID: " + id);
        System.out.println("REQUEST STATUS: " + status);
        System.out.println("PROVIDER EMAIL: " + provider.getEmail());
        System.out.println("TARGET CUSTOMER EMAIL: " + customer.getEmail());
        System.out.println(
                "TOKEN EXISTS: "
                        + (customerFcmToken != null
                        && !customerFcmToken.isBlank())
        );
        System.out.println(
                "TOKEN LENGTH: "
                        + (customerFcmToken == null
                        ? 0 : customerFcmToken.length())
        );

        if (customerFcmToken != null && !customerFcmToken.isBlank()) {
            System.out.println(
                    "TOKEN FINGERPRINT: "
                            + Integer.toHexString(customerFcmToken.hashCode())
            );
        }

        System.out.println("NOTIFICATION TITLE: " + notification.getTitle());
        System.out.println("NOTIFICATION TYPE: " + notification.getType());
        System.out.println("REFERENCE ID: " + notification.getReferenceId());
        System.out.println("========================================");

        if (customerFcmToken != null && !customerFcmToken.isBlank()) {
            try {
                System.out.println(
                        "Sending push notification to customer..."
                );

                firebaseMessagingService.sendPushNotification(
                        customerFcmToken,
                        notification.getTitle(),
                        notification.getMessage()
                );

                System.out.println(
                        "Customer push notification call completed."
                );
            } catch (Exception e) {
                System.err.println(
                        "Customer push notification failed: "
                                + e.getMessage()
                );
                e.printStackTrace();
            }
        } else {
            System.err.println(
                    "Customer FCM token is missing. Push skipped."
            );
        }

        return ResponseEntity.ok(updatedRequest);
    }

    // ==================== PROVIDER BOOKINGS ====================

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
                                "ACCEPTED".equals(request.getStatus())
                                        || "COMPLETED".equals(request.getStatus())
                        )
                        .toList();

        return ResponseEntity.ok(bookings);
    }
}
