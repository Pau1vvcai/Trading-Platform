package com.cai.trading.application;

import com.cai.trading.domain.Account;
import com.cai.trading.domain.Quantity;
import com.cai.trading.domain.Symbol;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** 测试辅助边界：记录操作前后总现金/总持仓并验证守恒与非负。 */
public interface AssetInvariantChecker {
    AssetTotals capture(Collection<Account> accounts);
    void verifyUnchanged(AssetTotals before, Collection<Account> after);

    record AssetTotals(Map<String, BigDecimal> cashByCurrency,
                       Map<Symbol, Quantity> positionsBySymbol) {
        public AssetTotals {
            // 先规范化金额，再做不可变拷贝，避免 BigDecimal 的 scale 影响快照相等性。
            Map<String, BigDecimal> normalizedCash = new LinkedHashMap<>();
            cashByCurrency.forEach((currency, amount) ->
                    normalizedCash.put(currency, amount.stripTrailingZeros()));
            cashByCurrency = Map.copyOf(normalizedCash);
            positionsBySymbol = Map.copyOf(positionsBySymbol);
        }
    }
}

