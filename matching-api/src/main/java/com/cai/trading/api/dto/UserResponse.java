package com.cai.trading.api.dto;

import java.util.UUID;

public record UserResponse(UUID userId, String displayName) { }

