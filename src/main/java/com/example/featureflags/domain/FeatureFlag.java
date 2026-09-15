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
    private boolean enabled;               // global state for all users

    @Column(name = "rollout_percentage")
    private Integer rolloutPercentage;     // null is treated as 100 (everyone)

    @Version
    private Long version;                  // optimistic locking

    private Instant createdAt;
    private Instant updatedAt;

    protected FeatureFlag() {}

    public FeatureFlag(String name, String description, boolean enabled) {
        this(name, description, enabled, 100);
    }

    public FeatureFlag(String name, String description, boolean enabled, int rolloutPercentage) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
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
    public int getRolloutPercentage() { return rolloutPercentage == null ? 100 : rolloutPercentage; }
    public void setRolloutPercentage(int rolloutPercentage) { this.rolloutPercentage = rolloutPercentage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
