package com.example.featureflags.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean enabled;   // global state for all users

    @Version
    private Long version;      // optimistic locking for concurrent updates

    private Instant createdAt;
    private Instant updatedAt;

    protected FeatureFlag() {}

    public FeatureFlag(String name, String description, boolean enabled) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
    }

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
