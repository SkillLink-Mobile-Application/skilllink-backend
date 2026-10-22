package com.skilllink.controller;

import com.skilllink.entity.ChatMessage;
import com.skilllink.entity.User;
import com.skilllink.repository.ChatMessageRepository;
import com.skilllink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.skilllink.service.FirebaseMessagingService;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final FirebaseMessagingService firebaseMessagingService;

    public ChatController(
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository,
            FirebaseMessagingService firebaseMessagingService
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.firebaseMessagingService = firebaseMessagingService;
    }

    // ==================== Send Message ====================

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(
            Authentication authentication,
            @RequestParam String receiverEmail,
            @RequestParam String message
    ) {

        String senderEmail = authentication.getName();

        Optional<User> sender =
                userRepository.findByEmail(senderEmail);

        if (sender.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Sender not found");
        }

        Optional<User> receiver =
                userRepository.findByEmail(receiverEmail);

        if (receiver.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Receiver not found");
        }

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body("Message cannot be empty");
        }

        ChatMessage chatMessage =
                new ChatMessage();

        chatMessage.setSender(sender.get());
        chatMessage.setReceiver(receiver.get());
        chatMessage.setMessage(message.trim());
        chatMessage.setSentAt(LocalDateTime.now());

        ChatMessage savedMessage =
                chatMessageRepository.save(chatMessage);

// Send notification to the receiver
        User receiverUser = receiver.get();
        User senderUser = sender.get();

        String receiverFcmToken = receiverUser.getFcmToken();

        String senderName = senderUser.getName();

        String notificationBody =
                senderName + " sent you a message: "
                        + savedMessage.getMessage();

        firebaseMessagingService.sendPushNotification(
                receiverFcmToken,
                "New Message",
                notificationBody
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

        String userEmail = authentication.getName();

        Optional<User> user =
                userRepository.findByEmail(userEmail);

        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        Optional<User> otherUser =
                userRepository.findByEmail(otherUserEmail);

        if (otherUser.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
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

        Map<String, Object> response =
                new HashMap<>();

        response.put("id", message.getId());

        response.put(
                "senderEmail",
                message.getSender().getEmail()
        );

        response.put(
                "receiverEmail",
                message.getReceiver().getEmail()
        );

        response.put(
                "message",
                message.getMessage()
        );

        response.put(
                "sentAt",
                message.getSentAt()
        );

        return response;
    }
}