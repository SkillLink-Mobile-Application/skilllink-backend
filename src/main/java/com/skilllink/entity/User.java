
package com.skilllink.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skilllink.enums.Role;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String phone;
    private String profileImage;

    // ==================== Firebase FCM Token ====================

    @Column(length = 500)
    private String fcmToken;

    // ==================== Email Verification ====================

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = true;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "email_verification_token")
    private String emailVerificationToken;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "email_verification_token_expiry")
    private LocalDateTime emailVerificationTokenExpiry;

    /*
     * This field is used only to receive the role
     * from the registration request.
     * It will NOT be stored as a column in the users table.
     */
    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Role role;

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<UserRole> roles = new ArrayList<>();

    public User() {
    }

    public User(
            String name,
            String email,
            String password,
            String phone
    ) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
    }

    // ==================== ID Get/Set ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // ==================== Name Get/Set ====================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // ==================== Email Get/Set ====================

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // ==================== Password Get/Set ====================

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // ==================== Phone Get/Set ====================

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    // ==================== Profile Image Get/Set ====================

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    // ==================== FCM Token Get/Set ====================

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    // ==================== Email Verification Get/Set ====================

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getEmailVerificationToken() {
        return emailVerificationToken;
    }

    public void setEmailVerificationToken(String emailVerificationToken) {
        this.emailVerificationToken = emailVerificationToken;
    }

    public LocalDateTime getEmailVerificationTokenExpiry() {
        return emailVerificationTokenExpiry;
    }

    public void setEmailVerificationTokenExpiry(
            LocalDateTime emailVerificationTokenExpiry
    ) {
        this.emailVerificationTokenExpiry = emailVerificationTokenExpiry;
    }

    // ==================== Role Get/Set ====================

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    // ==================== User Roles Get/Set ====================

    public List<UserRole> getRoles() {
        return roles;
    }

    public void setRoles(List<UserRole> roles) {
        this.roles = roles;
    }

    public void addRole(UserRole userRole) {
        roles.add(userRole);
        userRole.setUser(this);
    }

    public void removeRole(UserRole userRole) {
        roles.remove(userRole);
        userRole.setUser(null);
    }
}
