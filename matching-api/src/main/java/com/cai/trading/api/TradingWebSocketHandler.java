package com.cai.trading.api;

import com.cai.trading.analytics.RealtimeEvent;
import com.cai.trading.analytics.SequencedEventHub;
import com.cai.trading.api.dto.OrderBookResponse;
import com.cai.trading.api.dto.TradeResponse;
import com.cai.trading.application.OrderApplicationService;
import com.cai.trading.application.TradeRepository;
import com.cai.trading.domain.Symbol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** 连接建立时先发完整快照，之后只发带序号的增量事件。 */
public final class TradingWebSocketHandler extends TextWebSocketHandler {
    private final SequencedEventHub hub;
    private final OrderApplicationService orders;
    private final TradeRepository trades;
    private final ObjectMapper mapper;
    private final Map<String, AutoCloseable> subscriptions = new ConcurrentHashMap<>();

    public TradingWebSocketHandler(SequencedEventHub hub, OrderApplicationService orders,
                                   TradeRepository trades, ObjectMapper mapper) {
        this.hub = hub; this.orders = orders; this.trades = trades; this.mapper = mapper;
    }

    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String value = session.getUri() == null ? null : UriComponentsBuilder.fromUri(session.getUri())
                .build().getQueryParams().getFirst("symbol");
        Symbol symbol = new Symbol(value == null ? "BTC-USD" : value);
        String channel = symbol.value();
        // 先订阅并暂存事件，再发送快照，封住“快照与订阅之间丢事件”的竞态窗口。
        List<RealtimeEvent> pending = new ArrayList<>();
        boolean[] live = {false};
        AutoCloseable subscription = hub.subscribe(channel, event -> {
            synchronized (pending) {
                if (!live[0]) pending.add(event); else sendQuietly(session, event);
            }
        });
        subscriptions.put(session.getId(), subscription);
        long snapshotSequence = hub.currentSequence(channel);
        send(session, Map.of("type", "SNAPSHOT", "sequence", snapshotSequence,
                "orderBook", OrderBookResponse.from(orders.getOrderBook(symbol)),
                "trades", trades.findBySymbol(symbol).reversed().stream().limit(50)
                        .map(TradeResponse::from).toList()));
        synchronized (pending) {
            pending.stream().filter(event -> event.sequence() > snapshotSequence)
                    .forEach(event -> sendQuietly(session, event));
            pending.clear();
            live[0] = true;
        }
    }

    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        AutoCloseable subscription = subscriptions.remove(session.getId());
        if (subscription != null) try { subscription.close(); } catch (Exception ignored) { }
    }

    private void sendQuietly(WebSocketSession session, RealtimeEvent event) {
        try { send(session, event); } catch (IOException error) {
            try { session.close(CloseStatus.SERVER_ERROR); } catch (IOException ignored) { }
        }
    }

    private void send(WebSocketSession session, Object value) throws IOException {
        synchronized (session) {
            if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(value)));
        }
    }
}
