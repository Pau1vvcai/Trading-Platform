package com.cai.trading.api;

import com.cai.trading.analytics.*;
import com.cai.trading.application.*;
import com.cai.trading.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** 显式装配端口与适配器，让核心模块不需要任何 Spring 注解。 */
@Configuration
public class MatchingConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SequencedEventHub sequencedEventHub(Clock clock) {
        return new SequencedEventHub(1_000, clock);
    }

    @Bean
    TradingEventPublisher tradingEventPublisher(SequencedEventHub events,
                                                 io.micrometer.core.instrument.MeterRegistry metrics) {
        return new RealtimeTradingEventPublisher(events, metrics);
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    OrderRepository orderRepository(JdbcTemplate jdbc) {
        return new PostgresOrderRepository(jdbc);
    }

    @Bean
    AccountRepository accountRepository(JdbcTemplate jdbc) {
        return new PostgresAccountRepository(jdbc);
    }

    @Bean
    TradeRepository tradeRepository(JdbcTemplate jdbc) {
        return new PostgresTradeRepository(jdbc);
    }

    @Bean
    TransactionRunner transactionRunner(TransactionTemplate template) {
        return new SpringTransactionRunner(template);
    }

    @Bean
    IdempotencyStore idempotencyStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        return new PostgresIdempotencyStore(jdbc, mapper);
    }

    @Bean
    MatchingEngineRegistry matchingEngineRegistry(Clock clock) {
        return new InMemoryMatchingEngineRegistry(clock);
    }

    @Bean
    RiskChecker riskChecker() {
        return new DefaultRiskChecker();
    }

    @Bean
    SettlementService settlementService() {
        return new DefaultSettlementService();
    }

    @Bean
    OrderApplicationService orderApplicationService(
            OrderRepository orders,
            AccountRepository accounts,
            TradeRepository trades,
            RiskChecker riskChecker,
            SettlementService settlement,
            IdempotencyStore idempotency,
            TransactionRunner transactions,
            MatchingEngineRegistry engines,
            Clock clock,
            TradingEventPublisher events) {
        return new DefaultOrderApplicationService(orders, accounts, trades, riskChecker,
                settlement, idempotency, transactions, engines, clock, events);
    }

    @Bean
    MarketDataRepository marketDataRepository(JdbcTemplate jdbc) {
        return new PostgresMarketDataRepository(jdbc);
    }

    @Bean
    ResearchResultRepository researchResultRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        return new PostgresResearchResultRepository(jdbc, mapper);
    }

    @Bean
    CsvMarketDataImporter csvMarketDataImporter(MarketDataRepository repository) {
        return new CsvMarketDataImporter(repository, 500);
    }

    @Bean
    MarketReplayService marketReplayService() { return new MarketReplayService(); }

    @Bean
    BacktestEngine backtestEngine() { return new BacktestEngine(); }

    @Bean
    BacktestJobRepository backtestJobRepository() { return new InMemoryBacktestJobRepository(); }

    @Bean(destroyMethod = "close")
    AsyncBacktestService asyncBacktestService(BacktestEngine engine, BacktestJobRepository jobs,
                                              Clock clock, SequencedEventHub events) {
        BacktestJobListener listener = job -> events.publish("backtest:" + job.id(),
                "BACKTEST_JOB_CHANGED", java.util.Map.of(
                        "jobId", job.id().toString(), "status", job.status().name(),
                        "progress", job.progress()));
        return new AsyncBacktestService(engine, jobs, 2, clock, listener);
    }

    @Bean
    TradingWebSocketHandler tradingWebSocketHandler(SequencedEventHub events,
                                                     OrderApplicationService orders,
                                                     TradeRepository trades,
                                                     ObjectMapper mapper) {
        return new TradingWebSocketHandler(events, orders, trades, mapper);
    }

    @Bean
    UserAccountService userAccountService(JdbcTemplate jdbc, AccountRepository accounts,
                                          TransactionTemplate transactions) {
        return new JdbcUserAccountService(jdbc, accounts, transactions);
    }

    @Bean
    OrderBookRecoveryService recoveryService(OrderRepository orders, MatchingEngineRegistry engines) {
        return new OrderBookRecoveryService(orders, engines);
    }

    @Bean
    ApplicationRunner recoverOrderBooks(OrderRepository orders, OrderBookRecoveryService recovery) {
        return arguments -> recovery.recover(orders.findActiveSymbols());
    }
}
