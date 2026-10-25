package com.skilllink.entity;

import com.skilllink.enums.ProviderApprovalStatus;
import com.skilllink.enums.ProviderBadge;
import jakarta.persistence.*;

@Entity
@Table(name = "provider_profiles")
public class ProviderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String location;

    private Integer experience;

    @Column(length = 1000)
    private String description;

    // Profile picture path / URL
    @Column(length = 500)
    private String profileImage;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Provider application approval status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderApprovalStatus approvalStatus =
            ProviderApprovalStatus.PENDING;

    // Provider verification badge
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderBadge badge = ProviderBadge.NONE;

    @Column(name = "nic_number")
    private String nicNumber;

    @Column(name = "service_category")
    private String serviceCategory;

    @Column(name = "certificate_name")
    private String certificateName;

    @Column(name = "issuing_institute")
    private String issuingInstitute;

    @Column(name = "certificate_file")
    private String certificateFile;

    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted = false;

    public ProviderProfile() {
    }

    public ProviderProfile(
            String location,
            Integer experience,
            String description,
            String profileImage,
            User user
    ) {
        this.location = location;
        this.experience = experience;
        this.description = description;
        this.profileImage = profileImage;
        this.user = user;
        this.approvalStatus = ProviderApprovalStatus.PENDING;
        this.badge = ProviderBadge.NONE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getExperience() {
        return experience;
    }

    public void setExperience(Integer experience) {
        this.experience = experience;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ProviderApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ProviderApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public ProviderBadge getBadge() {
        return badge;
    }

    public void setBadge(ProviderBadge badge) {
        this.badge = badge;
    }

    public String getNicNumber() {
        return nicNumber;
    }

    public void setNicNumber(String nicNumber) {
        this.nicNumber = nicNumber;
    }

    public String getServiceCategory() {
        return serviceCategory;
    }

    public void setServiceCategory(String serviceCategory) {
        this.serviceCategory = serviceCategory;
    }

    public String getCertificateName() {
        return certificateName;
    }

    public void setCertificateName(String certificateName) {
        this.certificateName = certificateName;
    }

    public String getIssuingInstitute() {
        return issuingInstitute;
    }

    public void setIssuingInstitute(String issuingInstitute) {
        this.issuingInstitute = issuingInstitute;
    }

    public String getCertificateFile() {
        return certificateFile;
    }

    public void setCertificateFile(String certificateFile) {
        this.certificateFile = certificateFile;
    }

    public boolean isTermsAccepted() {
        return termsAccepted;
    }

    public void setTermsAccepted(boolean termsAccepted) {
        this.termsAccepted = termsAccepted;
    }
}