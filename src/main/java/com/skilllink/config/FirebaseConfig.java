package com.skilllink.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initializeFirebase() {

        try {

            FileInputStream serviceAccount =
                    new FileInputStream(
                            "firebase/firebase-service-account.json"
                    );

            FirebaseOptions options =
                    FirebaseOptions.builder()
                            .setCredentials(
                                    GoogleCredentials
                                            .fromStream(serviceAccount)
                            )
                            .build();

            if (FirebaseApp.getApps().isEmpty()) {

                FirebaseApp.initializeApp(options);

                System.out.println(
                        "Firebase Admin SDK initialized successfully!"
                );
            }

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to initialize Firebase Admin SDK",
                    e
            );
        }
    }
}