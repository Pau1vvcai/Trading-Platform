package com.cai.trading.analytics;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 按频道生成严格递增序列，并保留有限事件窗口。客户端发现序列断层后应重新拉取快照。
 */
public final class SequencedEventHub {
    private final Map<String, ChannelState> channels = new ConcurrentHashMap<>();
    private final int historyLimit;
    private final Clock clock;

    public SequencedEventHub(int historyLimit, Clock clock) {
        if (historyLimit <= 0) throw new IllegalArgumentException("historyLimit must be positive");
        this.historyLimit = historyLimit;
        this.clock = Objects.requireNonNull(clock);
    }

    public RealtimeEvent publish(String channel, String type, Map<String, Object> payload) {
        ChannelState state = channels.computeIfAbsent(channel, ignored -> new ChannelState());
        RealtimeEvent event;
        synchronized (state) {
            event = new RealtimeEvent(state.sequence.incrementAndGet(), channel, type,
                    Instant.now(clock), payload);
            state.history.addLast(event);
            while (state.history.size() > historyLimit) state.history.removeFirst();
        }
        state.listeners.forEach(listener -> listener.accept(event));
        return event;
    }

    public AutoCloseable subscribe(String channel, Consumer<RealtimeEvent> listener) {
        ChannelState state = channels.computeIfAbsent(channel, ignored -> new ChannelState());
        state.listeners.add(listener);
        return () -> state.listeners.remove(listener);
    }

    public List<RealtimeEvent> eventsAfter(String channel, long sequence) {
        ChannelState state = channels.computeIfAbsent(channel, ignored -> new ChannelState());
        synchronized (state) {
            return state.history.stream().filter(event -> event.sequence() > sequence).toList();
        }
    }

    public long currentSequence(String channel) {
        return channels.computeIfAbsent(channel, ignored -> new ChannelState()).sequence.get();
    }

    private static final class ChannelState {
        private final AtomicLong sequence = new AtomicLong();
        private final ArrayDeque<RealtimeEvent> history = new ArrayDeque<>();
        private final CopyOnWriteArrayList<Consumer<RealtimeEvent>> listeners = new CopyOnWriteArrayList<>();
    }
}
