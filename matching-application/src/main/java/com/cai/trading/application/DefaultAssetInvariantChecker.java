package com.cai.trading.application;

import com.cai.trading.domain.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** 把系统看成闭合账本，测试每次正常交易操作前后的现金和证券总量。 */
public final class DefaultAssetInvariantChecker implements AssetInvariantChecker {
    @Override
    public AssetTotals capture(Collection<Account> accounts) {
        Map<String, BigDecimal> cash = new HashMap<>();
        Map<Symbol, Quantity> positions = new HashMap<>();
        for (Account account : accounts) {
            cash.merge(account.availableCash().currency(),
                    account.availableCash().amount().add(account.frozenCash().amount()), BigDecimal::add);
            account.positions().forEach((symbol, position) -> positions.merge(
                    symbol, position.available().add(position.frozen()), Quantity::add));
        }
        return new AssetTotals(Map.copyOf(cash), Map.copyOf(positions));
    }

    @Override
    public void verifyUnchanged(AssetTotals before, Collection<Account> after) {
        AssetTotals current = capture(after);
        if (!before.equals(current)) {
            throw new IllegalStateException("Asset invariant violated. before=" + before + ", after=" + current);
        }
    }
}

