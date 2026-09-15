package com.example.featureflags;

import com.example.featureflags.api.Dtos.CreateFlagRequest;
import com.example.featureflags.api.Dtos.EvaluationResponse;
import com.example.featureflags.domain.FeatureFlag;
import com.example.featureflags.domain.UserOverride;
import com.example.featureflags.error.ConflictException;
import com.example.featureflags.error.NotFoundException;
import com.example.featureflags.repo.FeatureFlagRepository;
import com.example.featureflags.repo.UserOverrideRepository;
import com.example.featureflags.service.FlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pure unit tests: no Spring context, repositories mocked. */
class FlagServiceTest {

    private FeatureFlagRepository flags;
    private UserOverrideRepository overrides;
    private FlagService service;

    @BeforeEach
    void setUp() {
        flags = mock(FeatureFlagRepository.class);
        overrides = mock(UserOverrideRepository.class);
        service = new FlagService(flags, overrides);
    }

    @Test
    void evaluate_usesGlobalWhenNoOverride() {
        when(flags.findByName("f")).thenReturn(Optional.of(new FeatureFlag("f", null, true)));
        when(overrides.findByFlagIdAndUserId(any(), eq("u1"))).thenReturn(Optional.empty());

        EvaluationResponse r = service.evaluate("f", "u1");

        assertThat(r.enabled()).isTrue();
        assertThat(r.reason()).isEqualTo(FlagService.REASON_GLOBAL);
    }

    @Test
    void evaluate_overrideWinsOverGlobal() {
        when(flags.findByName("f")).thenReturn(Optional.of(new FeatureFlag("f", null, true)));
        when(overrides.findByFlagIdAndUserId(any(), eq("u1")))
                .thenReturn(Optional.of(new UserOverride(1L, "u1", false)));

        EvaluationResponse r = service.evaluate("f", "u1");

        assertThat(r.enabled()).isFalse();
        assertThat(r.reason()).isEqualTo(FlagService.REASON_OVERRIDE);
    }

    @Test
    void evaluate_unknownFlagThrowsNotFound() {
        when(flags.findByName("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.evaluate("missing", "u1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_duplicateThrowsConflict() {
        when(flags.existsByName("f")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateFlagRequest("f", null, true)))
                .isInstanceOf(ConflictException.class);
        verify(flags, never()).save(any());
    }
}
