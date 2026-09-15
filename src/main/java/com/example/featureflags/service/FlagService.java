package com.example.featureflags.service;

import com.example.featureflags.api.Dtos.CreateFlagRequest;
import com.example.featureflags.api.Dtos.EvaluationResponse;
import com.example.featureflags.domain.FeatureFlag;
import com.example.featureflags.domain.UserOverride;
import com.example.featureflags.error.ConflictException;
import com.example.featureflags.error.NotFoundException;
import com.example.featureflags.repo.FeatureFlagRepository;
import com.example.featureflags.repo.UserOverrideRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.featureflags.config.CacheConfig.EVAL_CACHE;

@Service
public class FlagService {

    public static final String REASON_OVERRIDE = "USER_OVERRIDE";
    public static final String REASON_GLOBAL = "GLOBAL";

    private final FeatureFlagRepository flags;
    private final UserOverrideRepository overrides;

    public FlagService(FeatureFlagRepository flags, UserOverrideRepository overrides) {
        this.flags = flags;
        this.overrides = overrides;
    }

    // ---------- Flag CRUD ----------

    @Transactional
    public FeatureFlag create(CreateFlagRequest req) {
        if (flags.existsByName(req.name())) {
            throw new ConflictException("Flag '%s' already exists".formatted(req.name()));
        }
        return flags.save(new FeatureFlag(req.name(), req.description(), req.defaultEnabled()));
    }

    @Transactional(readOnly = true)
    public FeatureFlag get(String name) {
        return flags.findByName(name)
                .orElseThrow(() -> new NotFoundException("Flag '%s' not found".formatted(name)));
    }

    @Transactional(readOnly = true)
    public List<FeatureFlag> list() {
        return flags.findAll(Sort.by("name"));
    }

    @Transactional
    @CacheEvict(cacheNames = EVAL_CACHE, allEntries = true)
    public void delete(String name) {
        FeatureFlag f = get(name);
        overrides.deleteByFlagId(f.getId());
        flags.delete(f);
    }

    // ---------- State management ----------

    /** Global toggle affects every user -> evict all cached evaluations. */
    @Transactional
    @CacheEvict(cacheNames = EVAL_CACHE, allEntries = true)
    public FeatureFlag setGlobal(String name, boolean enabled) {
        FeatureFlag f = get(name);
        f.setEnabled(enabled);
        return flags.saveAndFlush(f);
    }

    /** Upsert a per-user override -> evict only that user's entry. */
    @Transactional
    @CacheEvict(cacheNames = EVAL_CACHE, key = "#name + ':' + #userId")
    public UserOverride setUserOverride(String name, String userId, boolean enabled) {
        FeatureFlag f = get(name);
        UserOverride o = overrides.findByFlagIdAndUserId(f.getId(), userId)
                .orElseGet(() -> new UserOverride(f.getId(), userId, enabled));
        o.setEnabled(enabled);
        return overrides.save(o);
    }

    @Transactional
    @CacheEvict(cacheNames = EVAL_CACHE, key = "#name + ':' + #userId")
    public void removeUserOverride(String name, String userId) {
        FeatureFlag f = get(name);
        UserOverride o = overrides.findByFlagIdAndUserId(f.getId(), userId)
                .orElseThrow(() -> new NotFoundException(
                        "No override for user '%s' on flag '%s'".formatted(userId, name)));
        overrides.delete(o);
    }

    // ---------- Evaluation ----------

    /**
     * Precedence: user override > global state.
     * Cache hit: O(1). Cache miss: 2 indexed DB lookups, then the result is cached.
     * Not @Transactional on purpose: a cache hit should not open a DB transaction.
     * Unknown flag throws 404, and exceptions are never cached.
     */
    @Cacheable(cacheNames = EVAL_CACHE, key = "#name + ':' + #userId")
    public EvaluationResponse evaluate(String name, String userId) {
        FeatureFlag f = get(name);
        return overrides.findByFlagIdAndUserId(f.getId(), userId)
                .map(o -> new EvaluationResponse(name, userId, o.isEnabled(), REASON_OVERRIDE))
                .orElseGet(() -> new EvaluationResponse(name, userId, f.isEnabled(), REASON_GLOBAL));
    }

    /** Bulk evaluation for SDK bootstrap: 2 queries total, no N+1. */
    @Transactional(readOnly = true)
    public Map<String, Boolean> evaluateAll(String userId) {
        Map<Long, UserOverride> byFlag = overrides.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserOverride::getFlagId, Function.identity()));
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (FeatureFlag f : list()) {
            UserOverride o = byFlag.get(f.getId());
            result.put(f.getName(), o != null ? o.isEnabled() : f.isEnabled());
        }
        return result;
    }
}
