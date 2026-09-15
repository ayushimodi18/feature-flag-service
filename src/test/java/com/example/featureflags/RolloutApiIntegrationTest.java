package com.example.featureflags;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RolloutApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String uniqueName() {
        return "roll-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void createWithZeroRollout_thenRaiseTo100_invalidatesCache() throws Exception {
        String n = uniqueName();
        mvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"defaultEnabled\":true,\"rolloutPercentage\":0}".formatted(n)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rolloutPercentage").value(0));

        mvc.perform(get("/api/v1/flags/" + n + "/evaluate").param("userId", "u1"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("ROLLOUT"));

        mvc.perform(put("/api/v1/flags/" + n + "/rollout").contentType(APPLICATION_JSON)
                        .content("{\"percentage\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolloutPercentage").value(100));

        mvc.perform(get("/api/v1/flags/" + n + "/evaluate").param("userId", "u1"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));
    }

    @Test
    void defaultRollout_is100() throws Exception {
        String n = uniqueName();
        mvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"defaultEnabled\":true}".formatted(n)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rolloutPercentage").value(100));
    }

    @Test
    void invalidPercentage_returns400() throws Exception {
        String n = uniqueName();
        mvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"defaultEnabled\":true,\"rolloutPercentage\":150}".formatted(n)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"defaultEnabled\":true}".formatted(n)))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/flags/" + n + "/rollout").contentType(APPLICATION_JSON)
                        .content("{\"percentage\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userOverride_winsOverZeroRollout() throws Exception {
        String n = uniqueName();
        mvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"defaultEnabled\":true,\"rolloutPercentage\":0}".formatted(n)))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/flags/" + n + "/users/beta-tester").contentType(APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/flags/" + n + "/evaluate").param("userId", "beta-tester"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("USER_OVERRIDE"));
    }
}
