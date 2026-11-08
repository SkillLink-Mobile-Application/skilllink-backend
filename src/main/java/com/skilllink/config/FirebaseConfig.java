//package com.skilllink.config;
//
//import com.google.auth.oauth2.GoogleCredentials;
//import com.google.firebase.FirebaseApp;
//import com.google.firebase.FirebaseOptions;
//import org.springframework.context.annotation.Configuration;
//
//import jakarta.annotation.PostConstruct;
//
//import java.io.FileInputStream;
//import java.io.IOException;
//
//@Configuration
//public class FirebaseConfig {
//
//    @PostConstruct
//    public void initializeFirebase() {
//
//        try {
//
//            FileInputStream serviceAccount =
//                    new FileInputStream(
//                            "firebase/firebase-service-account.json"
//                    );
//
//            FirebaseOptions options =
//                    FirebaseOptions.builder()
//                            .setCredentials(
//                                    GoogleCredentials
//                                            .fromStream(serviceAccount)
//                            )
//                            .build();
//
//            if (FirebaseApp.getApps().isEmpty()) {
//
//                FirebaseApp.initializeApp(options);
//
//                System.out.println(
//                        "Firebase Admin SDK initialized successfully!"
//                );
//            }
//
//        } catch (IOException e) {
//
//            throw new RuntimeException(
//                    "Failed to initialize Firebase Admin SDK",
//                    e
//            );
//        }
//    }
//}



package com.skilllink.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initializeFirebase() {

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        String firebaseCredentials =
                System.getenv("FIREBASE_SERVICE_ACCOUNT_BASE64");

        try {
            GoogleCredentials credentials;

            if (firebaseCredentials != null
                    && !firebaseCredentials.isBlank()) {

                byte[] decoded = Base64.getDecoder()
                        .decode(firebaseCredentials);

                credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(decoded)
                );

            } else {
                // Local development fallback
                try (FileInputStream serviceAccount =
                             new FileInputStream(
                                     "firebase/firebase-service-account.json"
                             )) {

                    credentials = GoogleCredentials.fromStream(
                            serviceAccount
                    );
                } catch (IOException e) {
                    System.out.println(
                            "Firebase credentials not configured. "
                                    + "Firebase notifications are disabled."
                    );
                    return;
                }
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            FirebaseApp.initializeApp(options);

            System.out.println(
                    "Firebase Admin SDK initialized successfully!"
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to initialize Firebase Admin SDK", e
            );
        }
    }
}
