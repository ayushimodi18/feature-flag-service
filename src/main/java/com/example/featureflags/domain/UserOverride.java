package com.example.featureflags.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "user_overrides",
       uniqueConstraints = @UniqueConstraint(columnNames = {"flag_id", "user_id"}),
       indexes = @Index(name = "idx_override_flag_user", columnList = "flag_id,user_id"))
public class UserOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flag_id", nullable = false)
    private Long flagId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(nullable = false)
    private boolean enabled;

    protected UserOverride() {}

    public UserOverride(Long flagId, String userId, boolean enabled) {
        this.flagId = flagId;
        this.userId = userId;
        this.enabled = enabled;
    }

    public Long getFlagId() { return flagId; }
    public String getUserId() { return userId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
