package com.cai.trading.api;

import com.cai.trading.api.dto.CreateUserRequest;
import com.cai.trading.api.dto.UserResponse;
import com.cai.trading.application.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserAccountService service;

    public UserController(UserAccountService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        var id = service.createUser(request.displayName());
        return new UserResponse(id.value(), request.displayName().trim());
    }
}

