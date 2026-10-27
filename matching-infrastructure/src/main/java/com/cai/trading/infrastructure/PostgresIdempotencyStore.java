package com.cai.trading.infrastructure;

import com.cai.trading.application.IdempotencyStore;
import com.cai.trading.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * PostgreSQL 是幂等事实的权威来源。此实现应在外层业务事务中调用，
 * 这样订单、资产变化和成功结果要么一起提交，要么一起回滚。
 */
public final class PostgresIdempotencyStore implements IdempotencyStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PostgresIdempotencyStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public <T> T executeOnce(AccountId accountId, String key, String requestDigest,
                             Class<T> resultType, Supplier<T> action) {
        int claimed = jdbc.update("""
                INSERT INTO idempotency_records(
                    account_id, idempotency_key, request_digest, status, result_type)
                VALUES (?, ?, ?, 'PROCESSING', ?)
                ON CONFLICT (account_id, idempotency_key) DO NOTHING
                """, accountId.value(), key, requestDigest, resultType.getName());

        if (claimed == 0) {
            StoredResult stored = jdbc.queryForObject("""
                    SELECT request_digest, status, result_type, result_payload, error_code, error_message
                    FROM idempotency_records
                    WHERE account_id = ? AND idempotency_key = ?
                    """, (rs, rowNum) -> new StoredResult(
                    rs.getString("request_digest"), rs.getString("status"),
                    rs.getString("result_type"), rs.getString("result_payload"),
                    rs.getString("error_code"), rs.getString("error_message")),
                    accountId.value(), key);
            if (!stored.digest().equals(requestDigest) || !stored.resultType().equals(resultType.getName())) {
                throw new DomainException(ErrorCode.DUPLICATE_REQUEST_CONFLICT,
                        "Idempotency key was used for another request");
            }
            if ("SUCCEEDED".equals(stored.status())) {
                return read(stored.payload(), resultType);
            }
            if ("REJECTED".equals(stored.status())) {
                throw new DomainException(ErrorCode.valueOf(stored.errorCode()), stored.errorMessage());
            }
            throw new DomainException(ErrorCode.DUPLICATE_REQUEST_CONFLICT,
                    "The original request is still processing");
        }

        try {
            T result = Objects.requireNonNull(action.get(), "idempotent result");
            jdbc.update("""
                    UPDATE idempotency_records
                    SET status = 'SUCCEEDED', result_payload = ?
                    WHERE account_id = ? AND idempotency_key = ?
                    """, write(result), accountId.value(), key);
            return result;
        } catch (DomainException error) {
            // 外层事务通常会回滚该占位行；记录字段保留给将来采用“拒绝也提交”的事务策略。
            jdbc.update("""
                    UPDATE idempotency_records
                    SET status = 'REJECTED', error_code = ?, error_message = ?
                    WHERE account_id = ? AND idempotency_key = ?
                    """, error.code().name(), error.getMessage(), accountId.value(), key);
            throw error;
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize idempotent result", error);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot deserialize idempotent result", error);
        }
    }

    private record StoredResult(String digest, String status, String resultType,
                                String payload, String errorCode, String errorMessage) { }
}

