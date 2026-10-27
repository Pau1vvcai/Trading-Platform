package com.cai.trading.analytics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.cai.trading.analytics.AnalyticsFixtures.bar;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketReplayServiceTest {
    @Test
    void should_replay_in_timestamp_order_without_mutating_input() {
        List<MarketBar> input = new ArrayList<>(List.of(bar(2, "12"), bar(0, "10"), bar(1, "11")));
        List<MarketBar> received = new ArrayList<>();
        long count = new MarketReplayService().replay(input, received::add);
        assertEquals(3, count);
        assertEquals(List.of("10", "11", "12"), received.stream()
                .map(item -> item.close().toPlainString()).toList());
        assertEquals("12", input.getFirst().close().toPlainString());
    }

    @Test
    void should_allow_empty_replay() {
        assertEquals(0, new MarketReplayService().replay(List.of(), ignored -> { }));
    }
}
