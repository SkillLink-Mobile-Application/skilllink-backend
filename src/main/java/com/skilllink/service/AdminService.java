package com.skilllink.service;

import com.skilllink.dto.AdminDtos.*;
import com.skilllink.entity.ProviderProfile;
import com.skilllink.entity.User;
import com.skilllink.entity.UserRole;
import com.skilllink.enums.ProviderApprovalStatus;
import com.skilllink.enums.Role;
import com.skilllink.repository.ProviderProfileRepository;
import com.skilllink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AdminService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    // No look-alike characters (0/O, 1/l/I)
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminMailService mailService;

    public AdminService(
            UserRepository userRepository,
            ProviderProfileRepository providerProfileRepository,
            PasswordEncoder passwordEncoder,
            AdminMailService mailService
    ) {
        this.userRepository = userRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
    }

    // ================= HELPERS =================

    private boolean hasRole(User user, Role role) {
        return user.getRoles().stream().anyMatch(r -> r.getRole() == role);
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    // Checked against the database (not just the token) so a demoted admin loses access at once.
    private void requireSuperAdmin(String currentEmail) {
        User caller = userRepository.findByEmail(currentEmail).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed."));
        if (!hasRole(caller, Role.SUPER_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a Super Admin can manage administrators.");
        }
    }

    private long countSuperAdmins() {
        return userRepository.findAll().stream()
                .filter(u -> hasRole(u, Role.SUPER_ADMIN))
                .count();
    }

    private Role parseAdminRole(String value) {
        if ("SUPER_ADMIN".equals(value)) return Role.SUPER_ADMIN;
        if ("ADMIN".equals(value)) return Role.ADMIN;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Choose a valid admin role.");
    }

    private String generateTempPassword() {
        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
            }
            String p = sb.toString();
            if (p.chars().anyMatch(Character::isUpperCase)
                    && p.chars().anyMatch(Character::isLowerCase)
                    && p.chars().anyMatch(Character::isDigit)) {
                return p;
            }
        }
    }

    private String displayName(User user) {
        return user.getName() == null || user.getName().isBlank()
                ? user.getEmail()
                : user.getName();
    }

    private String title(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
    }

    private boolean isApplication(ProviderProfile p) {
        return p.getUser() != null
                && p.getNicNumber() != null
                && !p.getNicNumber().isBlank();
    }

    private UserRow toUserRow(User u) {

        List<String> roles = u.getRoles().stream()
                .map(UserRole::getRole)
                .filter(role -> role == Role.CUSTOMER || role == Role.PROVIDER)
                .distinct()
                .sorted(Comparator.comparingInt(role ->
                        role == Role.CUSTOMER ? 1 : 2
                ))
                .map(role -> role == Role.CUSTOMER ? "Customer" : "Provider")
                .toList();

        return new UserRow(
                u.getId(),
                displayName(u),
                u.getEmail(),
                roles,
                u.isBlocked() ? "Blocked" : "Active"
        );
    }

    private AdminRow toAdminRow(User u) {
        boolean isSuper = hasRole(u, Role.SUPER_ADMIN);
        return new AdminRow(
                u.getId(),
                displayName(u),
                u.getEmail(),
                isSuper ? "Super Admin" : "Admin",
                isSuper ? "SUPER_ADMIN" : "ADMIN"
        );
    }

    private ProviderApplicationRow toApplicationRow(ProviderProfile p) {
        User u = p.getUser();
        String status = p.getApprovalStatus() == null
                ? "Pending"
                : title(p.getApprovalStatus().name());
        return new ProviderApplicationRow(
                p.getId(),
                displayName(u),
                u.getEmail(),
                u.getPhone(),
                p.getServiceCategory(),
                p.getNicNumber(),
                p.getLocation(),
                p.getExperience(),
                p.getDescription(),
                p.getCertificateFile(),
                status
        );
    }

    // ================= DASHBOARD =================

    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        List<User> all = userRepository.findAll();

        long admins = all.stream().filter(u -> hasRole(u, Role.ADMIN)).count();
        long providers = all.stream()
                .filter(u -> !hasRole(u, Role.ADMIN) && hasRole(u, Role.PROVIDER))
                .count();
        long totalUsers = all.size() - admins;

        long pending = providerProfileRepository.findAll().stream()
                .filter(this::isApplication)
                .filter(p -> p.getApprovalStatus() == null
                        || p.getApprovalStatus() == ProviderApprovalStatus.PENDING)
                .count();

        return new StatsResponse(totalUsers, providers, pending, admins);
    }

    // ================= USERS =================

    @Transactional(readOnly = true)
    public List<UserRow> listUsers() {
        return userRepository.findAll().stream()
                .filter(u -> !hasRole(u, Role.ADMIN))
                .sorted(Comparator.comparing(User::getId))
                .map(this::toUserRow)
                .toList();
    }

    @Transactional
    public UserRow setBlocked(Long id, boolean blocked) {
        User user = findUser(id);

        if (hasRole(user, Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Admin accounts are managed in Admin Management.");
        }

        user.setBlocked(blocked);
        return toUserRow(userRepository.save(user));
    }

    // ================= ADMINS (Super Admin only) =================

    @Transactional(readOnly = true)
    public List<AdminRow> listAdmins(String currentEmail) {
        requireSuperAdmin(currentEmail);

        return userRepository.findAll().stream()
                .filter(u -> hasRole(u, Role.ADMIN))
                .sorted(Comparator.comparing(User::getId))
                .map(this::toAdminRow)
                .toList();
    }

    @Transactional
    public AdminRow createAdmin(CreateAdminRequest request, String currentEmail) {
        requireSuperAdmin(currentEmail);

        String name = request.name() == null ? "" : request.name().trim();
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        Role targetRole = parseAdminRole(request.role());

        if (name.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name must be at least 2 characters.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a valid email address.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email already exists.");
        }

        String temporaryPassword = generateTempPassword();

        User user = new User(name, email, passwordEncoder.encode(temporaryPassword), null);
        user.setEmailVerified(true);
        user.setMustChangePassword(true);
        user.addRole(new UserRole(user, Role.ADMIN));
        if (targetRole == Role.SUPER_ADMIN) {
            user.addRole(new UserRole(user, Role.SUPER_ADMIN));
        }

        User saved = userRepository.save(user);

        // If the email can't be sent this throws and the new account is rolled back.
        mailService.sendAdminCredentials(
                email,
                name,
                targetRole == Role.SUPER_ADMIN ? "Super Admin" : "Admin",
                temporaryPassword
        );

        return toAdminRow(saved);
    }

    @Transactional
    public AdminRow updateAdmin(Long id, UpdateAdminRequest request, String currentEmail) {
        requireSuperAdmin(currentEmail);

        User user = findUser(id);

        if (!hasRole(user, Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found");
        }

        String name = request.name() == null ? "" : request.name().trim();
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        Role targetRole = parseAdminRole(request.role());
        boolean isSelf = user.getEmail().equalsIgnoreCase(currentEmail);

        if (name.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name must be at least 2 characters.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a valid email address.");
        }

        // ----- email -----
        if (!email.equalsIgnoreCase(user.getEmail())) {
            // The login token is tied to the email, so changing your own would break your session.
            if (isSelf) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "You can't change the email of the account you're signed in with.");
            }
            if (userRepository.existsByEmail(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "An account with this email already exists.");
            }
            user.setEmail(email);
        }

        // ----- role -----
        boolean currentlySuper = hasRole(user, Role.SUPER_ADMIN);
        boolean wantSuper = targetRole == Role.SUPER_ADMIN;

        if (currentlySuper != wantSuper) {
            if (isSelf) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "You can't change your own role.");
            }
            if (currentlySuper && countSuperAdmins() <= 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "There must be at least one Super Admin.");
            }
            if (wantSuper) {
                user.addRole(new UserRole(user, Role.SUPER_ADMIN));
            } else {
                user.getRoles().removeIf(r -> r.getRole() == Role.SUPER_ADMIN);
            }
        }

        user.setName(name);
        return toAdminRow(userRepository.save(user));
    }

    @Transactional
    public void deleteAdmin(Long id, String currentEmail) {
        requireSuperAdmin(currentEmail);

        User user = findUser(id);

        if (!hasRole(user, Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found");
        }
        if (user.getEmail().equalsIgnoreCase(currentEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You can't delete your own account.");
        }
        if (hasRole(user, Role.SUPER_ADMIN) && countSuperAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The last Super Admin can't be deleted.");
        }

        boolean hasOtherRoles = user.getRoles().stream()
                .anyMatch(r -> r.getRole() != Role.ADMIN && r.getRole() != Role.SUPER_ADMIN);

        if (hasOtherRoles) {
            // Keep the customer/provider account, only remove admin access.
            user.getRoles().removeIf(r ->
                    r.getRole() == Role.ADMIN || r.getRole() == Role.SUPER_ADMIN);
            userRepository.save(user);
        } else {
            userRepository.delete(user);
        }
    }

    // ================= PROVIDER APPLICATIONS =================

    @Transactional(readOnly = true)
    public List<ProviderApplicationRow> listApplications() {
        return providerProfileRepository.findAll().stream()
                .filter(this::isApplication)
                .sorted(Comparator.comparing(ProviderProfile::getId).reversed())
                .map(this::toApplicationRow)
                .toList();
    }

    @Transactional
    public ProviderApplicationRow setApplicationStatus(Long id, ProviderApprovalStatus status) {
        ProviderProfile profile = providerProfileRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        profile.setApprovalStatus(status);
        return toApplicationRow(providerProfileRepository.save(profile));
    }
}