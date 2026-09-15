package com.example.featureflags;

import com.example.featureflags.api.Dtos.EvaluationResponse;
import com.example.featureflags.domain.FeatureFlag;
import com.example.featureflags.repo.FeatureFlagRepository;
import com.example.featureflags.repo.UserOverrideRepository;
import com.example.featureflags.service.FlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RolloutLogicTest {

    private FeatureFlagRepository flags;
    private FlagService service;

    @BeforeEach
    void setUp() {
        flags = mock(FeatureFlagRepository.class);
        UserOverrideRepository overrides = mock(UserOverrideRepository.class); // returns Optional.empty()
        service = new FlagService(flags, overrides);
    }

    private void givenFlag(boolean enabled, int pct) {
        when(flags.findByName("f")).thenReturn(Optional.of(new FeatureFlag("f", null, enabled, pct)));
    }

    @Test
    void bucket_isDeterministicAndInRange() {
        int b = FlagService.bucket("f", "user-42");
        assertThat(b).isBetween(0, 99);
        assertThat(FlagService.bucket("f", "user-42")).isEqualTo(b);
    }

    @Test
    void zeroPercent_enablesNobody() {
        givenFlag(true, 0);
        IntStream.range(0, 200).forEach(i -> {
            EvaluationResponse r = service.evaluate("f", "u" + i);
            assertThat(r.enabled()).isFalse();
            assertThat(r.reason()).isEqualTo(FlagService.REASON_ROLLOUT);
        });
    }

    @Test
    void hundredPercent_enablesEveryone() {
        givenFlag(true, 100);
        IntStream.range(0, 200).forEach(i ->
                assertThat(service.evaluate("f", "u" + i).enabled()).isTrue());
    }

    @Test
    void globalOff_winsOverRollout() {
        givenFlag(false, 50);
        EvaluationResponse r = service.evaluate("f", "u1");
        assertThat(r.enabled()).isFalse();
        assertThat(r.reason()).isEqualTo(FlagService.REASON_GLOBAL);
    }

    @Test
    void fiftyPercent_splitsUsersRoughlyInHalf() {
        givenFlag(true, 50);
        long enabled = IntStream.range(0, 2000)
                .filter(i -> service.evaluate("f", "user-" + i).enabled())
                .count();
        assertThat(enabled).isBetween(800L, 1200L);   // ~50% with tolerance
    }
}
