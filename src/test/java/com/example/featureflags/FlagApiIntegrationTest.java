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
class FlagApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String uniqueName() {
        return "flag-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void createFlag(String name, boolean defaultEnabled) throws Exception {
        mvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"description\":\"test\",\"defaultEnabled\":%s}"
                                .formatted(name, defaultEnabled)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/flags/" + name))
                .andExpect(jsonPath("$.enabled").value(defaultEnabled));
    }

    private void toggle(String url, boolean enabled) throws Exception {
        mvc.perform(put(url).contentType(APPLICATION_JSON)
                        .content("{\"enabled\":%s}".formatted(enabled)))
                .andExpect(status().isOk());
    }

    @Test
    void createAndGetFlag() throws Exception {
        String n = uniqueName();
        createFlag(n, true);
        mvc.perform(get("/api/v1/flags/" + n))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(n));
    }

    @Test
    void duplicateFlag_returns409() throws Exception {
        String n = uniqueName();
        createFlag(n, false);
        mvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"defaultEnabled\":true}".formatted(n)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidName_returns400WithFieldError() throws Exception {
        mvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Bad Name!\",\"defaultEnabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void missingDefaultEnabled_returns400() throws Exception {
        mvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"valid-name\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/v1/flags").contentType(APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownFlag_returns404() throws Exception {
        mvc.perform(get("/api/v1/flags/does-not-exist")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/flags/does-not-exist/evaluate").param("userId", "u1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void evaluateWithoutUserId_returns400() throws Exception {
        String n = uniqueName();
        createFlag(n, true);
        mvc.perform(get("/api/v1/flags/" + n + "/evaluate"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userOverride_takesPrecedenceOverGlobal() throws Exception {
        String n = uniqueName();
        createFlag(n, false);
        toggle("/api/v1/flags/" + n + "/users/alice", true);

        mvc.perform(get("/api/v1/flags/" + n + "/evaluate").param("userId", "alice"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("USER_OVERRIDE"));
        mvc.perform(get("/api/v1/flags/" + n + "/evaluate").param("userId", "bob"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));
    }

    @Test
    void globalToggle_invalidatesCachedEvaluation() throws Exception {
        String n = uniqueName();
        createFlag(n, false);
        // first call populates the cache
        mvc.perform(get("/api/v1/flags/" + n + "/evaluate").param("userId", "u1"))
                .andExpect(jsonPath("$.enabled").value(false));

        toggle("/api/v1/flags/" + n + "/global", true);

        // must NOT return the stale cached value
        mvc.perform(get("/api/v1/flags/" + n + "/evaluate").param("userId", "u1"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void userOverride_invalidatesCachedEvaluation() throws Exception {
        String n = uniqueName();
        createFlag(n, false);
        mvc.perform(get("/api/v1/flags/" + n + "/evaluate").param("userId", "u9"))
                .andExpect(jsonPath("$.enabled").value(false));

        toggle("/api/v1/flags/" + n + "/users/u9", true);

        mvc.perform(get("/api/v1/flags/" + n + "/evaluate").param("userId", "u9"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("USER_OVERRIDE"));
    }

    @Test
    void removingOverride_fallsBackToGlobal() throws Exception {
        String n = uniqueName();
        createFlag(n, true);
        toggle("/api/v1/flags/" + n + "/users/u1", false);

        mvc.perform(delete("/api/v1/flags/" + n + "/users/u1")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/flags/" + n + "/evaluate").param("userId", "u1"))
                .andExpect(jsonPath("$.enabled").value(true));
        // deleting again -> 404
        mvc.perform(delete("/api/v1/flags/" + n + "/users/u1")).andExpect(status().isNotFound());
    }

    @Test
    void deleteFlag_thenGet_returns404() throws Exception {
        String n = uniqueName();
        createFlag(n, true);
        mvc.perform(delete("/api/v1/flags/" + n)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/flags/" + n)).andExpect(status().isNotFound());
    }

    @Test
    void bulkUserFlags_returnsAllFlagsForUser() throws Exception {
        String n = uniqueName();
        createFlag(n, false);
        toggle("/api/v1/flags/" + n + "/users/carol", true);
        mvc.perform(get("/api/v1/users/carol/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags['" + n + "']").value(true));
    }
}
