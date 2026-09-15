package com.example.featureflags.repo;

import com.example.featureflags.domain.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {
    Optional<FeatureFlag> findByName(String name);
    boolean existsByName(String name);
}
