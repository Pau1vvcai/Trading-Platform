package com.cai.trading.application;

import com.cai.trading.domain.Account;

/** 下单前风险预检查；检查通过不等于资产已冻结。 */
public interface RiskChecker {
    void check(Account account, PlaceOrderCommand command);
}

