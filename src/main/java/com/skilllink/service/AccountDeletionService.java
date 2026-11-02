
package com.skilllink.service;

import com.skilllink.entity.Review;
import com.skilllink.entity.ServiceRequest;
import com.skilllink.entity.User;
import com.skilllink.entity.UserRole;
import com.skilllink.enums.Role;
import com.skilllink.repository.ChatMessageRepository;
import com.skilllink.repository.NotificationRepository;
import com.skilllink.repository.ProviderAvailabilityRepository;
import com.skilllink.repository.ProviderProfileRepository;
import com.skilllink.repository.ReviewRepository;
import com.skilllink.repository.ServiceRepository;
import com.skilllink.repository.ServiceRequestRepository;
import com.skilllink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ReviewRepository reviewRepository;
    private final ServiceRepository serviceRepository;
    private final ProviderAvailabilityRepository availabilityRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final NotificationRepository notificationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public AccountDeletionService(
            UserRepository userRepository,
            ServiceRequestRepository serviceRequestRepository,
            ReviewRepository reviewRepository,
            ServiceRepository serviceRepository,
            ProviderAvailabilityRepository availabilityRepository,
            ProviderProfileRepository providerProfileRepository,
            NotificationRepository notificationRepository,
            ChatMessageRepository chatMessageRepository
    ) {
        this.userRepository = userRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.reviewRepository = reviewRepository;
        this.serviceRepository = serviceRepository;
        this.availabilityRepository = availabilityRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.notificationRepository = notificationRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public String deleteRole(String email, Role selectedRole) {

        if (selectedRole == null ||
                (selectedRole != Role.CUSTOMER &&
                        selectedRole != Role.PROVIDER)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only CUSTOMER or PROVIDER role can be deleted."
            );
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found."
                ));

        UserRole roleToRemove = user.getRoles()
                .stream()
                .filter(userRole -> userRole.getRole() == selectedRole)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "You do not have this role."
                ));

        Long userId = user.getId();

        // Delete reviews and service requests belonging
        // to the selected role.
        deleteRoleReviewsAndRequests(user, selectedRole);

        // Remove provider-specific information only
        // when deleting the PROVIDER role.
        if (selectedRole == Role.PROVIDER) {
            serviceRepository.deleteByProviderId(userId);
            availabilityRepository.deleteByProviderId(userId);
            providerProfileRepository.deleteByUserId(userId);
        }

        // Remove only the selected role.
        user.removeRole(roleToRemove);

        if (user.getRoles().isEmpty()) {

            // Delete any remaining reviews and requests
            // before deleting the final user record.
            deleteAllRemainingReviewsAndRequests(user);

            // These belong to the complete user account.
            notificationRepository.deleteByUserId(userId);
            chatMessageRepository.deleteBySenderIdOrReceiverId(
                    userId,
                    userId
            );

            userRepository.delete(user);

            return "Account deleted successfully.";
        }

        userRepository.save(user);

        return selectedRole.name() +
                " role deleted successfully. Your other role is still active.";
    }

    private void deleteRoleReviewsAndRequests(
            User user,
            Role selectedRole
    ) {
        Long userId = user.getId();

        List<ServiceRequest> allRequests =
                serviceRequestRepository.findByCustomerIdOrProviderId(
                        userId,
                        userId
                );

        List<ServiceRequest> selectedRequests =
                new ArrayList<>();

        for (ServiceRequest request : allRequests) {

            if (selectedRole == Role.CUSTOMER &&
                    request.getCustomer().getId().equals(userId)) {
                selectedRequests.add(request);
            }

            if (selectedRole == Role.PROVIDER &&
                    request.getProvider().getId().equals(userId)) {
                selectedRequests.add(request);
            }
        }

        List<Review> reviews = new ArrayList<>();

        if (selectedRole == Role.CUSTOMER) {
            reviews.addAll(reviewRepository.findByCustomerId(userId));
        } else {
            reviews.addAll(reviewRepository.findByProviderId(userId));
        }

        // Also delete reviews connected to selected service requests.
        for (ServiceRequest request : selectedRequests) {
            reviewRepository.findByServiceRequestId(request.getId())
                    .ifPresent(reviews::add);
        }

        if (!reviews.isEmpty()) {
            reviewRepository.deleteAll(reviews);
        }

        if (!selectedRequests.isEmpty()) {
            serviceRequestRepository.deleteAll(selectedRequests);
        }
    }

    private void deleteAllRemainingReviewsAndRequests(User user) {

        Long userId = user.getId();

        List<ServiceRequest> remainingRequests =
                serviceRequestRepository.findByCustomerIdOrProviderId(
                        userId,
                        userId
                );

        List<Review> remainingReviews = new ArrayList<>();

        remainingReviews.addAll(
                reviewRepository.findByCustomerId(userId)
        );

        remainingReviews.addAll(
                reviewRepository.findByProviderId(userId)
        );

        for (ServiceRequest request : remainingRequests) {
            reviewRepository.findByServiceRequestId(request.getId())
                    .ifPresent(remainingReviews::add);
        }

        if (!remainingReviews.isEmpty()) {
            reviewRepository.deleteAll(remainingReviews);
        }

        if (!remainingRequests.isEmpty()) {
            serviceRequestRepository.deleteAll(remainingRequests);
        }
    }
}
