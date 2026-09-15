package com.example.featureflags.api;

import com.example.featureflags.api.Dtos.UserFlagsResponse;
import com.example.featureflags.service.FlagService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

/** SDK bootstrap: evaluate every flag for one user in a single call. */
@RestController
@RequestMapping("/api/v1/users")
public class UserFlagsController {

    private final FlagService service;

    public UserFlagsController(FlagService service) {
        this.service = service;
    }

    @GetMapping("/{userId}/flags")
    public UserFlagsResponse allFlags(@PathVariable @NotBlank @Size(max = 128) String userId) {
        return new UserFlagsResponse(userId, service.evaluateAll(userId));
    }
}
