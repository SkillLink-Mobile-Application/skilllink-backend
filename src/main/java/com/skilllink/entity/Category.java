
package com.skilllink.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    // Predefined icon name (e.g. zap, wrench, hammer)
    @Column(length = 100)
    private String icon;

    // Short description displayed in category cards
    @Column(length = 500)
    private String description;

    // Cloudinary image URL (optional)
    @Column(length = 1000)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active = true;

    public Category() {
    }

    public Category(String name, String icon) {
        this.name = name;
        this.icon = icon;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
