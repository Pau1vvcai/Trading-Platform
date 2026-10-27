package com.cai.trading.application;

import com.cai.trading.domain.Account;
import com.cai.trading.domain.AccountId;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findById(AccountId id);
    void save(Account account);
}

