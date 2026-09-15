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

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import static com.example.featureflags.config.CacheConfig.EVAL_CACHE;

@Service
public class FlagService {

    public static final String REASON_OVERRIDE = "USER_OVERRIDE";
    public static final String REASON_GLOBAL = "GLOBAL";
    public static final String REASON_ROLLOUT = "ROLLOUT";

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
        int rollout = req.rolloutPercentage() == null ? 100 : req.rolloutPercentage();
        return flags.save(new FeatureFlag(req.name(), req.description(), req.defaultEnabled(), rollout));
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

    /** Rollout change affects many users -> evict all cached evaluations. */
    @Transactional
    @CacheEvict(cacheNames = EVAL_CACHE, allEntries = true)
    public FeatureFlag setRollout(String name, int percentage) {
        FeatureFlag f = get(name);
        f.setRolloutPercentage(percentage);
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
     * Precedence: user override > global OFF > percentage rollout.
     * Cache hit: O(1). Cache miss: 2 indexed DB lookups, then the result is cached.
     * Not @Transactional on purpose: a cache hit should not open a DB transaction.
     * Unknown flag throws 404, and exceptions are never cached.
     */
    @Cacheable(cacheNames = EVAL_CACHE, key = "#name + ':' + #userId")
    public EvaluationResponse evaluate(String name, String userId) {
        FeatureFlag f = get(name);
        Optional<UserOverride> override = overrides.findByFlagIdAndUserId(f.getId(), userId);
        if (override.isPresent()) {
            return new EvaluationResponse(name, userId, override.get().isEnabled(), REASON_OVERRIDE);
        }
        return evaluateWithoutOverride(f, userId);
    }

    /** Bulk evaluation for SDK bootstrap: 2 queries total, no N+1. */
    @Transactional(readOnly = true)
    public Map<String, Boolean> evaluateAll(String userId) {
        Map<Long, UserOverride> byFlag = overrides.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserOverride::getFlagId, Function.identity()));
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (FeatureFlag f : list()) {
            UserOverride o = byFlag.get(f.getId());
            boolean enabled = (o != null) ? o.isEnabled() : evaluateWithoutOverride(f, userId).enabled();
            result.put(f.getName(), enabled);
        }
        return result;
    }

    private EvaluationResponse evaluateWithoutOverride(FeatureFlag f, String userId) {
        if (!f.isEnabled()) {
            return new EvaluationResponse(f.getName(), userId, false, REASON_GLOBAL);
        }
        int pct = f.getRolloutPercentage();
        if (pct >= 100) {
            return new EvaluationResponse(f.getName(), userId, true, REASON_GLOBAL);
        }
        boolean inRollout = bucket(f.getName(), userId) < pct;
        return new EvaluationResponse(f.getName(), userId, inRollout, REASON_ROLLOUT);
    }

    /** Deterministic bucket 0..99: the same user always lands in the same bucket for a flag. */
    public static int bucket(String flagName, String userId) {
        CRC32 crc = new CRC32();
        crc.update((flagName + ":" + userId).getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % 100);
    }
}
