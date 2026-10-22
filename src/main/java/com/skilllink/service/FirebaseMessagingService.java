package com.skilllink.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import org.springframework.stereotype.Service;

@Service
public class FirebaseMessagingService {

    public void sendPushNotification(
            String fcmToken,
            String title,
            String body
    ) {

        System.out.println("\n========== FCM SEND DEBUG ==========");
        System.out.println("TITLE: " + title);
        System.out.println("TOKEN EXISTS: "
                + (fcmToken != null && !fcmToken.isBlank()));

        if (fcmToken == null || fcmToken.isBlank()) {
            System.err.println("ERROR: FCM token is empty.");
            System.out.println("====================================");
            return;
        }

        System.out.println("TOKEN LENGTH: " + fcmToken.length());
        System.out.println("TOKEN FINGERPRINT: "
                + Integer.toHexString(fcmToken.hashCode()));

        try {
            Notification notification =
                    Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build();

            Message firebaseMessage =
                    Message.builder()
                            .setToken(fcmToken)
                            .setNotification(notification)
                            .build();

            String response =
                    FirebaseMessaging.getInstance()
                            .send(firebaseMessage);

            System.out.println("FCM SEND STATUS: SUCCESS");
            System.out.println("FIREBASE MESSAGE ID: " + response);

        } catch (FirebaseMessagingException e) {

            System.err.println("FCM SEND STATUS: FAILED");
            System.err.println("FIREBASE ERROR CODE: "
                    + e.getMessagingErrorCode());
            System.err.println("ERROR MESSAGE: " + e.getMessage());

            e.printStackTrace();

        } catch (Exception e) {

            System.err.println("FCM UNEXPECTED ERROR: "
                    + e.getMessage());

            e.printStackTrace();

        } finally {
            System.out.println("====================================\n");
        }
    }
}