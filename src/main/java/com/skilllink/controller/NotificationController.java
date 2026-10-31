
package com.skilllink.controller;

import com.skilllink.entity.Notification;
import com.skilllink.entity.User;
import com.skilllink.entity.ChatMessage;

import com.skilllink.repository.NotificationRepository;
import com.skilllink.repository.UserRepository;
import com.skilllink.repository.ChatMessageRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;

    public NotificationController(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            ChatMessageRepository chatMessageRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    // ==================== Get Notifications ====================

    @GetMapping
    public ResponseEntity<?> getNotifications(
            Authentication authentication
    ) {
        Optional<User> user = getAuthenticatedUser(authentication);

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        List<Notification> notifications =
                notificationRepository
                        .findByUserIdOrderByCreatedAtDesc(
                                user.get().getId()
                        );

        List<Map<String, Object>> response =
                notifications.stream()
                        .map(this::createNotificationResponse)
                        .toList();

        return ResponseEntity.ok(response);
    }

    // ==================== Get Unread Count ====================

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            Authentication authentication
    ) {
        Optional<User> user = getAuthenticatedUser(authentication);

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        long count =
                notificationRepository
                        .countByUserIdAndIsReadFalse(
                                user.get().getId()
                        );

        return ResponseEntity.ok(Map.of("count", count));
    }

    // ==================== Mark One as Read ====================

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(
            Authentication authentication,
            @PathVariable Long id
    ) {
        Optional<User> user = getAuthenticatedUser(authentication);

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        Optional<Notification> optionalNotification =
                notificationRepository.findById(id);

        if (optionalNotification.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Notification not found");
        }

        Notification notification = optionalNotification.get();

        if (!notification.getUser().getId()
                .equals(user.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You can only update your own notifications");
        }

        notification.setRead(true);

        Notification updatedNotification =
                notificationRepository.save(notification);

        return ResponseEntity.ok(
                createNotificationResponse(updatedNotification)
        );
    }

    // ==================== Mark All as Read ====================

    @PutMapping("/mark-all-read")
    public ResponseEntity<?> markAllAsRead(
            Authentication authentication
    ) {
        Optional<User> user = getAuthenticatedUser(authentication);

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        List<Notification> notifications =
                notificationRepository
                        .findByUserIdOrderByCreatedAtDesc(
                                user.get().getId()
                        );

        for (Notification notification : notifications) {
            if (!notification.isRead()) {
                notification.setRead(true);
            }
        }

        notificationRepository.saveAll(notifications);

        long unreadCount =
                notificationRepository
                        .countByUserIdAndIsReadFalse(
                                user.get().getId()
                        );

        Map<String, Object> response = new HashMap<>();
        response.put("message", "All notifications marked as read");
        response.put("unreadCount", unreadCount);

        return ResponseEntity.ok(response);
    }

    // ==================== Delete One Notification ====================

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNotification(
            Authentication authentication,
            @PathVariable Long id
    ) {
        Optional<User> user = getAuthenticatedUser(authentication);

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        Optional<Notification> optionalNotification =
                notificationRepository.findById(id);

        if (optionalNotification.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Notification not found");
        }

        Notification notification = optionalNotification.get();

        if (!notification.getUser().getId()
                .equals(user.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You can only delete your own notifications");
        }

        notificationRepository.delete(notification);

        return ResponseEntity.noContent().build();
    }

    // ==================== Clear All Notifications ====================

    @DeleteMapping("/clear-all")
    public ResponseEntity<?> clearAllNotifications(
            Authentication authentication
    ) {
        Optional<User> user = getAuthenticatedUser(authentication);

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        notificationRepository.deleteByUserId(
                user.get().getId()
        );

        return ResponseEntity.noContent().build();
    }

    // ==================== Find Logged-in User ====================

    private Optional<User> getAuthenticatedUser(
            Authentication authentication
    ) {
        if (authentication == null
                || authentication.getName() == null) {
            return Optional.empty();
        }

        return userRepository.findByEmail(
                authentication.getName()
        );
    }

    // ==================== Notification Response ====================

    private Map<String, Object> createNotificationResponse(
            Notification notification
    ) {
        Map<String, Object> response = new HashMap<>();

        response.put("id", notification.getId());
        response.put("title", notification.getTitle());
        response.put("message", notification.getMessage());
        response.put("isRead", notification.isRead());
        response.put("createdAt", notification.getCreatedAt());
        response.put("type", notification.getType());
        response.put("referenceId", notification.getReferenceId());

        // Add sender details for chat notifications
        if ("NEW_MESSAGE".equalsIgnoreCase(notification.getType())
                && notification.getReferenceId() != null) {

            Optional<ChatMessage> optionalMessage =
                    chatMessageRepository.findById(
                            notification.getReferenceId()
                    );

            if (optionalMessage.isPresent()) {
                ChatMessage chatMessage = optionalMessage.get();

                response.put(
                        "senderEmail",
                        chatMessage.getSender().getEmail()
                );

                response.put(
                        "senderName",
                        chatMessage.getSender().getName()
                );
            }
        }

        return response;
    }
}
