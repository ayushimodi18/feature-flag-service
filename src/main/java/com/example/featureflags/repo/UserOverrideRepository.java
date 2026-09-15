package com.example.featureflags.repo;

import com.example.featureflags.domain.UserOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserOverrideRepository extends JpaRepository<UserOverride, Long> {
    Optional<UserOverride> findByFlagIdAndUserId(Long flagId, String userId);
    List<UserOverride> findByUserId(String userId);
    void deleteByFlagId(Long flagId);
}
