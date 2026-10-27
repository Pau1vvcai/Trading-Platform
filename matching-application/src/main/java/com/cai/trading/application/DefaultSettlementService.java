package com.cai.trading.application;

import com.cai.trading.domain.*;

/** 处理资产冻结、成交结算、限价改善退款和终态余量解冻。 */
public final class DefaultSettlementService implements SettlementService {
    @Override
    public void freezeFor(Order order, Account account, PlaceOrderCommand command) {
        if (order.side() == Side.BUY) {
            Money reservation = order.type() == OrderType.LIMIT
                    ? order.limitPrice().orElseThrow()
                        .multiply(order.originalQuantity(), account.availableCash().currency())
                    : command.maxSpend().orElseThrow(() ->
                        new DomainException(ErrorCode.INVALID_ORDER, "Market buy requires maxSpend"));
            account.freezeCash(reservation);
            order.reserveCash(reservation);
        } else {
            account.position(order.symbol()).freeze(order.originalQuantity());
        }
    }

    @Override
    public void settle(Trade trade, Order maker, Order taker, Account makerAccount, Account takerAccount) {
        Order buyerOrder = maker.side() == Side.BUY ? maker : taker;
        Order sellerOrder = maker.side() == Side.SELL ? maker : taker;
        Account buyer = accountFor(buyerOrder, maker, makerAccount, takerAccount);
        Account seller = accountFor(sellerOrder, maker, makerAccount, takerAccount);
        Money actualCost = trade.executionPrice().multiply(
                trade.executionQuantity(), buyer.availableCash().currency());

        buyer.debitFrozenCash(actualCost);
        buyerOrder.consumeReservedCash(actualCost);
        buyer.position(trade.symbol()).credit(trade.executionQuantity());

        seller.position(trade.symbol()).debitFrozen(trade.executionQuantity());
        seller.creditCash(actualCost);

        if (buyerOrder.type() == OrderType.LIMIT) {
            Money reservedForFill = buyerOrder.limitPrice().orElseThrow()
                    .multiply(trade.executionQuantity(), actualCost.currency());
            Money improvement = reservedForFill.subtract(actualCost);
            if (!improvement.isZero()) {
                buyer.releaseCash(improvement);
                buyerOrder.consumeReservedCash(improvement);
            }
        }
    }

    @Override
    public void releaseAfterCancel(Order order, Account account) {
        if (order.isActive()) {
            throw new DomainException(ErrorCode.ORDER_NOT_CANCELABLE, "Order must be terminal before release");
        }
        if (order.side() == Side.BUY) {
            Money release = order.releaseReservedCash();
            if (!release.isZero()) {
                account.releaseCash(release);
            }
        } else if (!order.remainingQuantity().isZero()) {
            account.position(order.symbol()).release(order.remainingQuantity());
        }
    }

    private static Account accountFor(Order order, Order maker,
                                      Account makerAccount, Account takerAccount) {
        Account account = order == maker ? makerAccount : takerAccount;
        if (!account.id().equals(order.accountId())) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Settlement account/order mismatch");
        }
        return account;
    }
}

