package com.cai.trading.application;

import com.cai.trading.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.cai.trading.application.ApplicationTestFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultSettlementServiceTest {
    private DefaultSettlementService settlement;

    @BeforeEach
    void setUp() {
        settlement = new DefaultSettlementService();
    }

    @Test
    void should_freeze_limit_buy_notional() {
        Account buyer = account("buyer", "100", "0");
        Order order = limit("buy", buyer, Side.BUY, "10", "5", 1);

        settlement.freezeFor(order, buyer, limitCommand(buyer, Side.BUY, "10", "5"));

        assertEquals(money("50"), buyer.availableCash());
        assertEquals(money("50"), buyer.frozenCash());
    }

    @Test
    void should_freeze_market_buy_max_spend_instead_of_unknown_notional() {
        Account buyer = account("buyer", "100", "0");
        Order order = market("buy", buyer, Side.BUY, "5", 1);

        settlement.freezeFor(order, buyer, marketBuyCommand(buyer, "5", "70"));

        assertEquals(money("30"), buyer.availableCash());
        assertEquals(money("70"), buyer.frozenCash());
    }

    @Test
    void should_freeze_sell_position() {
        Account seller = account("seller", "0", "5");
        Order order = limit("sell", seller, Side.SELL, "8", "5", 1);

        settlement.freezeFor(order, seller, limitCommand(seller, Side.SELL, "8", "5"));

        assertEquals(quantity("0"), seller.position(SYMBOL).available());
        assertEquals(quantity("5"), seller.position(SYMBOL).frozen());
    }

    @Test
    void should_settle_full_trade_and_release_limit_price_improvement() {
        Account seller = account("seller", "0", "5");
        Account buyer = account("buyer", "100", "0");
        Order makerSell = limit("sell", seller, Side.SELL, "8", "5", 1);
        Order takerBuy = limit("buy", buyer, Side.BUY, "10", "5", 2);
        settlement.freezeFor(makerSell, seller, limitCommand(seller, Side.SELL, "8", "5"));
        settlement.freezeFor(takerBuy, buyer, limitCommand(buyer, Side.BUY, "10", "5"));
        makerSell.fill(quantity("5"));
        takerBuy.fill(quantity("5"));

        settlement.settle(trade(makerSell, takerBuy, "8", "5"),
                makerSell, takerBuy, seller, buyer);

        assertEquals(money("60"), buyer.availableCash());
        assertEquals(money("0"), buyer.frozenCash());
        assertEquals(quantity("5"), buyer.position(SYMBOL).available());
        assertEquals(money("40"), seller.availableCash());
        assertEquals(quantity("0"), seller.position(SYMBOL).frozen());
    }

    @Test
    void should_settle_correct_accounts_when_buy_order_is_maker() {
        Account buyer = account("buyer", "100", "0");
        Account seller = account("seller", "0", "5");
        Order makerBuy = limit("buy", buyer, Side.BUY, "10", "5", 1);
        Order takerSell = limit("sell", seller, Side.SELL, "8", "5", 2);
        settlement.freezeFor(makerBuy, buyer, limitCommand(buyer, Side.BUY, "10", "5"));
        settlement.freezeFor(takerSell, seller, limitCommand(seller, Side.SELL, "8", "5"));
        makerBuy.fill(quantity("5"));
        takerSell.fill(quantity("5"));

        settlement.settle(trade(makerBuy, takerSell, "10", "5"),
                makerBuy, takerSell, buyer, seller);

        assertEquals(money("50"), buyer.availableCash());
        assertEquals(money("0"), buyer.frozenCash());
        assertEquals(quantity("5"), buyer.position(SYMBOL).available());
        assertEquals(money("50"), seller.availableCash());
        assertEquals(quantity("0"), seller.position(SYMBOL).frozen());
    }

    @Test
    void should_keep_only_remaining_limit_reservation_after_partial_fill() {
        Account seller = account("seller", "0", "5");
        Account buyer = account("buyer", "100", "0");
        Order makerSell = limit("sell", seller, Side.SELL, "8", "5", 1);
        Order takerBuy = limit("buy", buyer, Side.BUY, "10", "5", 2);
        settlement.freezeFor(makerSell, seller, limitCommand(seller, Side.SELL, "8", "5"));
        settlement.freezeFor(takerBuy, buyer, limitCommand(buyer, Side.BUY, "10", "5"));
        makerSell.fill(quantity("2"));
        takerBuy.fill(quantity("2"));

        settlement.settle(trade(makerSell, takerBuy, "8", "2"),
                makerSell, takerBuy, seller, buyer);

        assertEquals(money("54"), buyer.availableCash());
        assertEquals(money("30"), buyer.frozenCash());
        assertEquals(quantity("2"), buyer.position(SYMBOL).available());
        assertEquals(money("16"), seller.availableCash());
        assertEquals(quantity("3"), seller.position(SYMBOL).frozen());
    }

    @Test
    void should_release_only_unfilled_assets_after_partial_fill_is_canceled() {
        Account seller = account("seller", "0", "5");
        Account buyer = account("buyer", "100", "0");
        Order makerSell = limit("sell", seller, Side.SELL, "8", "5", 1);
        Order takerBuy = limit("buy", buyer, Side.BUY, "10", "5", 2);
        settlement.freezeFor(makerSell, seller, limitCommand(seller, Side.SELL, "8", "5"));
        settlement.freezeFor(takerBuy, buyer, limitCommand(buyer, Side.BUY, "10", "5"));
        makerSell.fill(quantity("2"));
        takerBuy.fill(quantity("2"));
        settlement.settle(trade(makerSell, takerBuy, "8", "2"),
                makerSell, takerBuy, seller, buyer);
        makerSell.cancel();
        takerBuy.cancel();

        settlement.releaseAfterCancel(makerSell, seller);
        settlement.releaseAfterCancel(takerBuy, buyer);

        assertEquals(money("84"), buyer.availableCash());
        assertEquals(money("0"), buyer.frozenCash());
        assertEquals(quantity("3"), seller.position(SYMBOL).available());
        assertEquals(quantity("0"), seller.position(SYMBOL).frozen());
    }

    @Test
    void should_release_unused_market_buy_budget_after_final_cancel() {
        Account buyer = account("buyer", "100", "0");
        Account seller = account("seller", "0", "1");
        Order marketBuy = market("market-buy", buyer, Side.BUY, "3", 2);
        Order makerSell = limit("sell", seller, Side.SELL, "8", "1", 1);
        PlaceOrderCommand command = new PlaceOrderCommand("key", buyer.id(), SYMBOL,
                Side.BUY, OrderType.MARKET, Optional.empty(), quantity("3"), Optional.of(money("30")));
        settlement.freezeFor(marketBuy, buyer, command);
        settlement.freezeFor(makerSell, seller, limitCommand(seller, Side.SELL, "8", "1"));
        marketBuy.fill(quantity("1"));
        makerSell.fill(quantity("1"));
        settlement.settle(trade(makerSell, marketBuy, "8", "1"), makerSell, marketBuy, seller, buyer);
        marketBuy.cancel();

        settlement.releaseAfterCancel(marketBuy, buyer);

        assertEquals(money("92"), buyer.availableCash());
        assertEquals(money("0"), buyer.frozenCash());
    }
}
