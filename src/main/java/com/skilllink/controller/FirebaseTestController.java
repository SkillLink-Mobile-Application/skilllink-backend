package com.skilllink.controller;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FirebaseTestController {

    @GetMapping("/api/firebase-test")
    public String testFirebase() {

        try {

            String token = "NEW_FCM_TOKEN_HERE";
            Message message = Message.builder()
                    .setToken(token)
                    .putData("test", "SkillLink FCM Test")
                    .build();

            String response =
                    FirebaseMessaging.getInstance()
                            .send(message);

            return "FCM SENT SUCCESSFULLY: " + response;

        } catch (Exception e) {

            e.printStackTrace();

            return "FCM SEND FAILED: "
                    + e.getClass().getName()
                    + " | "
                    + e.getMessage();
        }
    }
}