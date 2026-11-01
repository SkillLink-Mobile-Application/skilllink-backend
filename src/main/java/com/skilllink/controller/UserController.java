
package com.skilllink.controller;

import com.skilllink.entity.User;
import com.skilllink.entity.UserRole;
import com.skilllink.enums.Role;
import com.skilllink.repository.UserRepository;
import com.skilllink.service.JwtService;
import com.skilllink.service.EmailVerificationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import com.skilllink.service.AccountDeletionService;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final AccountDeletionService accountDeletionService;

    public UserController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailVerificationService emailVerificationService,
            AccountDeletionService accountDeletionService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
        this.accountDeletionService = accountDeletionService;
    }

    // ================= REGISTER =================

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {

        Role requestedRole = user.getRole();

        if (requestedRole == null ||
                (requestedRole != Role.CUSTOMER &&
                        requestedRole != Role.PROVIDER)) {
            return ResponseEntity.badRequest()
                    .body("Invalid registration role");
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Email is required");
        }

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Password is required");
        }

        String email = user.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Email is already registered");
        }

        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // New registrations must verify their email.
        user.setEmailVerified(false);

        User savedUser = userRepository.save(user);

        UserRole userRole = new UserRole(savedUser, requestedRole);
        savedUser.addRole(userRole);

        savedUser = userRepository.save(savedUser);

        // Send verification email after saving the user and role.
        emailVerificationService.sendVerificationEmail(savedUser);

        return ResponseEntity.ok(
                Map.of(
                        "message", "Registration successful. Please check your email to verify your account.",
                        "email", savedUser.getEmail(),
                        "emailVerified", false
                )
        );
    }

    // ================= VERIFY EMAIL =================

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Verification token is required");
        }

        Optional<User> optionalUser =
                userRepository.findByEmailVerificationToken(token);

        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Invalid verification link");
        }

        User user = optionalUser.get();

        if (user.getEmailVerificationTokenExpiry() == null ||
                user.getEmailVerificationTokenExpiry().isBefore(LocalDateTime.now())) {

            return ResponseEntity.badRequest()
                    .body("Verification link has expired. Please request a new link.");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);

        userRepository.save(user);

        return ResponseEntity.ok(
                "Your SkillLink email has been verified successfully. You can now log in."
        );
    }

    // ================= LOGIN =================

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {

        Optional<User> existingUser =
                userRepository.findByEmail(user.getEmail());

        if (existingUser.isPresent() &&
                passwordEncoder.matches(
                        user.getPassword(),
                        existingUser.get().getPassword()
                )) {

            User authenticatedUser = existingUser.get();

            if (!authenticatedUser.isEmailVerified()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Please verify your email before logging in.");
            }

            String token = jwtService.generateToken(authenticatedUser);

            List<String> roles = authenticatedUser.getRoles()
                    .stream()
                    .map(UserRole::getRole)
                    .map(Enum::name)
                    .toList();

            return ResponseEntity.ok(
                    Map.of(
                            "token", token,
                            "roles", roles
                    )
            );
        }

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body("Invalid email or password");
    }

    // ================= ADD ROLE =================

    @PostMapping("/add-role")
    public ResponseEntity<?> addRole(
            Authentication authentication,
            @RequestParam Role role
    ) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication required");
        }

        // Only CUSTOMER and PROVIDER roles can be added
        if (role != Role.CUSTOMER && role != Role.PROVIDER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Invalid role");
        }

        // Get the logged-in user's email from JWT authentication
        String email = authentication.getName();

        Optional<User> optionalUser =
                userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        User user = optionalUser.get();

        boolean alreadyHasRole = user.getRoles()
                .stream()
                .anyMatch(userRole -> userRole.getRole() == role);

        if (alreadyHasRole) {
            return ResponseEntity.badRequest()
                    .body("User already has this role");
        }

        UserRole newUserRole = new UserRole(user, role);

        user.addRole(newUserRole);
        userRepository.save(user);

        return ResponseEntity.ok(
                Map.of(
                        "message", "Role added successfully",
                        "email", user.getEmail(),
                        "role", role.name()
                )
        );
    }

    // ================= GET ALL USERS =================

    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // ================= GET ALL PROVIDERS =================

    @GetMapping("/providers")
    public ResponseEntity<?> getAllProviders() {

        List<User> providers = userRepository.findAll()
                .stream()
                .filter(user -> user.getRoles()
                        .stream()
                        .anyMatch(userRole ->
                                userRole.getRole() == Role.PROVIDER
                        )
                )
                .toList();

        return ResponseEntity.ok(providers);
    }

    // ================= GET USER PROFILE =================

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(
            @RequestParam String email
    ) {

        Optional<User> optionalUser =
                userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        User user = optionalUser.get();

        return ResponseEntity.ok(
                Map.of(
                        "id", user.getId(),
                        "name", user.getName(),
                        "email", user.getEmail(),
                        "phone", user.getPhone() == null ? "" : user.getPhone(),
                        "profileImage", user.getProfileImage() == null
                                ? ""
                                : user.getProfileImage()
                )
        );
    }

    // ================= UPDATE USER PROFILE =================

    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(
            @RequestParam String email,
            @RequestBody User updatedUser
    ) {

        Optional<User> optionalUser =
                userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        User user = optionalUser.get();

        user.setName(updatedUser.getName());
        user.setPhone(updatedUser.getPhone());

        User savedUser = userRepository.save(user);

        return ResponseEntity.ok(
                Map.of(
                        "id", savedUser.getId(),
                        "name", savedUser.getName(),
                        "email", savedUser.getEmail(),
                        "phone", savedUser.getPhone() == null
                                ? ""
                                : savedUser.getPhone()
                )
        );
    }

    // ================= CHANGE PASSWORD =================

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @RequestBody Map<String, String> request
    ) {

        String email = authentication.getName();

        Optional<User> optionalUser =
                userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        User user = optionalUser.get();

        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");

        if (currentPassword == null || currentPassword.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Current password is required");
        }

        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("New password is required");
        }

        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body("New password must be at least 6 characters");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Current password is incorrect");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            return ResponseEntity.badRequest()
                    .body("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(
                Map.of("message", "Password changed successfully")
        );
    }

    // ================= SAVE FCM TOKEN =================

    @PutMapping("/fcm-token")
    public ResponseEntity<?> saveFcmToken(
            Authentication authentication,
            @RequestBody Map<String, String> request
    ) {

        if (authentication == null ||
                authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User authentication required");
        }

        String email = authentication.getName();

        Optional<User> optionalUser =
                userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        }

        String fcmToken = request.get("fcmToken");

        if (fcmToken == null || fcmToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("FCM token is required");
        }

        User user = optionalUser.get();

        System.out.println("========== FCM TOKEN SAVE DEBUG ==========");
        System.out.println("ACCOUNT EMAIL: " + user.getEmail());
        System.out.println("TOKEN LENGTH: " + fcmToken.length());
        System.out.println(
                "TOKEN FINGERPRINT: "
                        + Integer.toHexString(fcmToken.hashCode())
        );

        user.setFcmToken(fcmToken);
        userRepository.save(user);

        System.out.println("FCM TOKEN SAVE SUCCESS");
        System.out.println("==========================================");

        return ResponseEntity.ok(
                Map.of("message", "FCM token saved successfully")
        );
    }

    // ================= DELETE SELECTED ROLE =================

    @DeleteMapping("/delete-role")
    public ResponseEntity<?> deleteRole(
            Authentication authentication,
            @RequestParam Role role
    ) {

        if (authentication == null ||
                authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User authentication required");
        }

        String email = authentication.getName();

        String message = accountDeletionService.deleteRole(email, role);

        return ResponseEntity.ok(
                Map.of("message", message)
        );
    }
}
