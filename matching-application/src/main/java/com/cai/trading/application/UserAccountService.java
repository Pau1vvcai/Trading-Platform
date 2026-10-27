package com.cai.trading.application;

import com.cai.trading.domain.*;

/** 管理模拟用户与初始资产；真实身份认证不属于 v0.1。 */
public interface UserAccountService {
    UserId createUser(String displayName);
    Account createAccount(UserId userId, String currency, Money initialCash);
    Account getAccount(AccountId accountId);
    Account creditPosition(AccountId accountId, Symbol symbol, Quantity quantity);
}
