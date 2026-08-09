package com.revature.ccvi.banking.dao;

import java.time.Instant;
import java.util.Optional;

import com.revature.ccvi.banking.model.TransactionType;

/**
 * Database agnostic history filter (US-17). Both DAO implementations translate the same
 * instance into either a parameterized WHERE clause or a BSON query document.
 */
public final class TransactionFilter {

    private final TransactionType type;
    private final Instant from;
    private final Instant to;
    private final int limit;

    private TransactionFilter(Builder builder) {
        this.type = builder.type;
        this.from = builder.from;
        this.to = builder.to;
        this.limit = builder.limit;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TransactionFilter none() {
        return builder().build();
    }

    public Optional<TransactionType> getType() {
        return Optional.ofNullable(type);
    }

    public Optional<Instant> getFrom() {
        return Optional.ofNullable(from);
    }

    public Optional<Instant> getTo() {
        return Optional.ofNullable(to);
    }

    public int getLimit() {
        return limit;
    }

    public boolean hasLimit() {
        return limit > 0;
    }

    @Override
    public String toString() {
        return "TransactionFilter{type=" + type + ", from=" + from + ", to=" + to + ", limit=" + limit + "}";
    }

    public static final class Builder {

        private TransactionType type;
        private Instant from;
        private Instant to;
        private int limit = 100;

        public Builder type(TransactionType type) {
            this.type = type;
            return this;
        }

        public Builder from(Instant from) {
            this.from = from;
            return this;
        }

        public Builder to(Instant to) {
            this.to = to;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder unlimited() {
            this.limit = 0;
            return this;
        }

        public TransactionFilter build() {
            if (from != null && to != null && from.isAfter(to)) {
                throw new IllegalArgumentException("Filter start date must not be after the end date");
            }
            return new TransactionFilter(this);
        }
    }
}
