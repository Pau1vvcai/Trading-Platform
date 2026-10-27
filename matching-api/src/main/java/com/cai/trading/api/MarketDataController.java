package com.cai.trading.api;

import com.cai.trading.api.dto.OrderBookResponse;
import com.cai.trading.api.dto.TradeResponse;
import com.cai.trading.application.OrderApplicationService;
import com.cai.trading.application.TradeRepository;
import com.cai.trading.domain.Symbol;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MarketDataController {
    private final OrderApplicationService orders;
    private final TradeRepository trades;

    public MarketDataController(OrderApplicationService orders, TradeRepository trades) {
        this.orders = orders;
        this.trades = trades;
    }

    @GetMapping("/order-books/{symbol}")
    public OrderBookResponse orderBook(@PathVariable("symbol") String symbol) {
        return OrderBookResponse.from(orders.getOrderBook(new Symbol(symbol)));
    }

    @GetMapping("/trades")
    public List<TradeResponse> trades(@RequestParam("symbol") String symbol) {
        return trades.findBySymbol(new Symbol(symbol)).stream().map(TradeResponse::from).toList();
    }
}

