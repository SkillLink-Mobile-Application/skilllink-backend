
package com.skilllink.controller;

import com.skilllink.entity.ChatMessage;
import com.skilllink.entity.Notification;
import com.skilllink.entity.User;
import com.skilllink.repository.ChatMessageRepository;
import com.skilllink.repository.NotificationRepository;
import com.skilllink.repository.UserRepository;
import com.skilllink.service.FirebaseMessagingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final FirebaseMessagingService firebaseMessagingService;

    public ChatController(
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            FirebaseMessagingService firebaseMessagingService
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.firebaseMessagingService = firebaseMessagingService;
    }

    // ==================== Send Message ====================

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(
            Authentication authentication,
            @RequestParam String receiverEmail,
            @RequestParam String message
    ) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized");
        }

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Message cannot be empty");
        }

        String senderEmail = authentication.getName();

        Optional<User> sender =
                userRepository.findByEmail(senderEmail);

        if (sender.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Sender not found");
        }

        Optional<User> receiver =
                userRepository.findByEmail(receiverEmail);

        if (receiver.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Receiver not found");
        }

        User senderUser = sender.get();
        User receiverUser = receiver.get();

        // Save chat message
        ChatMessage chatMessage = new ChatMessage();

        chatMessage.setSender(senderUser);
        chatMessage.setReceiver(receiverUser);
        chatMessage.setMessage(message.trim());
        chatMessage.setSentAt(LocalDateTime.now());

        ChatMessage savedMessage =
                chatMessageRepository.save(chatMessage);

        // Save notification in database
        Notification chatNotification = new Notification();

        chatNotification.setUser(receiverUser);
        chatNotification.setTitle("New Message");
        chatNotification.setMessage(
                senderUser.getName() + " sent you a message: "
                        + savedMessage.getMessage()
        );
        chatNotification.setRead(false);
        chatNotification.setCreatedAt(LocalDateTime.now());
        chatNotification.setType("NEW_MESSAGE");
        chatNotification.setReferenceId(savedMessage.getId());

        Notification savedNotification =
                notificationRepository.save(chatNotification);

        // Prepare FCM navigation data
        Map<String, String> notificationData = new HashMap<>();

        notificationData.put("type", "NEW_MESSAGE");
        notificationData.put(
                "senderEmail",
                senderUser.getEmail()
        );
        notificationData.put(
                "senderName",
                senderUser.getName()
        );
        notificationData.put(
                "receiverEmail",
                receiverUser.getEmail()
        );
        notificationData.put(
                "messageId",
                String.valueOf(savedMessage.getId())
        );
        notificationData.put(
                "notificationId",
                String.valueOf(savedNotification.getId())
        );

        // Send push notification
        firebaseMessagingService.sendPushNotification(
                receiverUser.getFcmToken(),
                "New Message",
                senderUser.getName() + " sent you a message: "
                        + savedMessage.getMessage(),
                notificationData
        );

        return ResponseEntity.ok(
                createMessageResponse(savedMessage)
        );
    }

    // ==================== Get Conversation ====================

    @GetMapping("/conversation")
    public ResponseEntity<?> getConversation(
            Authentication authentication,
            @RequestParam String otherUserEmail
    ) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized");
        }

        String userEmail = authentication.getName();

        Optional<User> user =
                userRepository.findByEmail(userEmail);

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        Optional<User> otherUser =
                userRepository.findByEmail(otherUserEmail);

        if (otherUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Other user not found");
        }

        List<ChatMessage> messages =
                chatMessageRepository
                        .findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderBySentAtAsc(
                                user.get().getId(),
                                otherUser.get().getId(),
                                otherUser.get().getId(),
                                user.get().getId()
                        );

        List<Map<String, Object>> response =
                messages.stream()
                        .map(this::createMessageResponse)
                        .toList();

        return ResponseEntity.ok(response);
    }

    // ==================== Response ====================

    private Map<String, Object> createMessageResponse(
            ChatMessage message
    ) {

        Map<String, Object> response = new HashMap<>();

        response.put("id", message.getId());
        response.put(
                "senderEmail",
                message.getSender().getEmail()
        );
        response.put(
                "receiverEmail",
                message.getReceiver().getEmail()
        );
        response.put("message", message.getMessage());
        response.put("sentAt", message.getSentAt());

        return response;
    }
}
