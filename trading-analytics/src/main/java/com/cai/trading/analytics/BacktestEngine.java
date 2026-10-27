package com.cai.trading.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 单标的、仅做多的确定性回测核心。信号在当根收盘价成交，手续费按成交额比例收取。
 * 这一简化假设会在阶段总结中明确，避免把结果误解为真实可交易收益。
 */
public final class BacktestEngine {
    private static final int SCALE = 8;

    public BacktestResult run(List<MarketBar> input, TradingStrategy strategy,
                              BigDecimal initialCapital, BigDecimal feeRate) {
        Objects.requireNonNull(strategy);
        if (initialCapital.signum() <= 0) throw new IllegalArgumentException("initialCapital must be positive");
        if (feeRate.signum() < 0 || feeRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("feeRate must be within [0, 1)");
        }
        List<MarketBar> bars = input.stream().sorted(Comparator.comparing(MarketBar::timestamp)).toList();
        if (bars.isEmpty()) throw new IllegalArgumentException("bars cannot be empty");
        if (bars.stream().anyMatch(bar -> !bar.symbol().equals(bars.getFirst().symbol()))) {
            throw new IllegalArgumentException("all bars must use one symbol");
        }

        BigDecimal cash = money(initialCapital);
        BigDecimal quantity = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal openCost = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalFees = BigDecimal.ZERO.setScale(SCALE);
        int wins = 0;
        int closedTrades = 0;
        BigDecimal peak = cash;
        BigDecimal maximumDrawdown = BigDecimal.ZERO;
        List<EquityPoint> curve = new ArrayList<>(bars.size());
        List<BacktestTrade> trades = new ArrayList<>();

        for (int index = 0; index < bars.size(); index++) {
            MarketBar bar = bars.get(index);
            StrategyDecision decision = strategy.decide(index, bars);
            BigDecimal price = bar.close();
            if (decision.action() == StrategyAction.BUY && cash.signum() > 0) {
                BigDecimal budget = cash.multiply(decision.allocation());
                BigDecimal unitCost = price.multiply(BigDecimal.ONE.add(feeRate));
                BigDecimal bought = budget.divide(unitCost, SCALE, RoundingMode.DOWN);
                if (bought.signum() > 0) {
                    BigDecimal notional = money(bought.multiply(price));
                    BigDecimal fee = money(notional.multiply(feeRate));
                    cash = money(cash.subtract(notional).subtract(fee));
                    quantity = quantity.add(bought);
                    openCost = openCost.add(notional).add(fee);
                    totalFees = totalFees.add(fee);
                    trades.add(new BacktestTrade(bar.timestamp(), StrategyAction.BUY, price,
                            bought, fee, cash));
                }
            } else if (decision.action() == StrategyAction.SELL && quantity.signum() > 0) {
                BigDecimal sold = quantity;
                BigDecimal notional = money(sold.multiply(price));
                BigDecimal fee = money(notional.multiply(feeRate));
                BigDecimal proceeds = notional.subtract(fee);
                cash = money(cash.add(proceeds));
                totalFees = totalFees.add(fee);
                if (proceeds.compareTo(openCost) > 0) wins++;
                closedTrades++;
                quantity = BigDecimal.ZERO.setScale(SCALE);
                openCost = BigDecimal.ZERO.setScale(SCALE);
                trades.add(new BacktestTrade(bar.timestamp(), StrategyAction.SELL, price,
                        sold, fee, cash));
            }

            BigDecimal equity = money(cash.add(quantity.multiply(price)));
            peak = peak.max(equity);
            BigDecimal drawdown = peak.signum() == 0 ? BigDecimal.ZERO
                    : peak.subtract(equity).divide(peak, SCALE, RoundingMode.HALF_UP);
            maximumDrawdown = maximumDrawdown.max(drawdown);
            curve.add(new EquityPoint(bar.timestamp(), equity, drawdown));
        }

        BigDecimal finalEquity = curve.getLast().equity();
        BigDecimal totalReturn = finalEquity.subtract(initialCapital)
                .divide(initialCapital, SCALE, RoundingMode.HALF_UP);
        BigDecimal winRate = closedTrades == 0 ? BigDecimal.ZERO.setScale(SCALE)
                : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(closedTrades), SCALE, RoundingMode.HALF_UP);
        return new BacktestResult(strategy.name(), money(initialCapital), finalEquity,
                totalReturn, maximumDrawdown, winRate, totalFees, curve, trades);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
