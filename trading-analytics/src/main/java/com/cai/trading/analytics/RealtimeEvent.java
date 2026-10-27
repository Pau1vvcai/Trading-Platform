package com.cai.trading.analytics;

import java.time.Instant;
import java.util.Map;

public record RealtimeEvent(long sequence, String channel, String type,
                            Instant occurredAt, Map<String, Object> payload) {
    public RealtimeEvent {
        payload = Map.copyOf(payload);
    }
}
