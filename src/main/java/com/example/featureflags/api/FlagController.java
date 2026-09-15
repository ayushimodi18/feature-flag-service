package com.example.featureflags.api;

import com.example.featureflags.api.Dtos.*;
import com.example.featureflags.domain.FeatureFlag;
import com.example.featureflags.domain.UserOverride;
import com.example.featureflags.service.FlagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/flags")
public class FlagController {

    private final FlagService service;

    public FlagController(FlagService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<FlagResponse> create(@Valid @RequestBody CreateFlagRequest req) {
        FeatureFlag f = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/flags/" + f.getName()))
                .body(FlagResponse.from(f));
    }

    @GetMapping
    public List<FlagResponse> list() {
        return service.list().stream().map(FlagResponse::from).toList();
    }

    @GetMapping("/{name}")
    public FlagResponse get(@PathVariable String name) {
        return FlagResponse.from(service.get(name));
    }

    @PutMapping("/{name}/global")
    public FlagResponse setGlobal(@PathVariable String name,
                                  @Valid @RequestBody ToggleRequest req) {
        return FlagResponse.from(service.setGlobal(name, req.enabled()));
    }

    @PutMapping("/{name}/users/{userId}")
    public OverrideResponse setUserOverride(@PathVariable String name,
                                            @PathVariable @NotBlank @Size(max = 128) String userId,
                                            @Valid @RequestBody ToggleRequest req) {
        UserOverride o = service.setUserOverride(name, userId, req.enabled());
        return new OverrideResponse(name, o.getUserId(), o.isEnabled());
    }

    @DeleteMapping("/{name}/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUserOverride(@PathVariable String name, @PathVariable String userId) {
        service.removeUserOverride(name, userId);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        service.delete(name);
    }

    @GetMapping("/{name}/evaluate")
    public EvaluationResponse evaluate(@PathVariable String name,
                                       @RequestParam @NotBlank @Size(max = 128) String userId) {
        return service.evaluate(name, userId);
    }
}
