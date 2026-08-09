package com.revature.ccvi.banking.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revature.ccvi.banking.model.TransactionType;

@DisplayName("TransactionFilter: the shared history filter contract (US-17)")
class TransactionFilterTest {

    @Test
    @DisplayName("defaults to no type or date bounds with a bounded page size")
    void defaultsAreBounded() {
        TransactionFilter filter = TransactionFilter.none();

        assertTrue(filter.getType().isEmpty());
        assertTrue(filter.getFrom().isEmpty());
        assertTrue(filter.getTo().isEmpty());
        assertTrue(filter.hasLimit());
        assertEquals(100, filter.getLimit());
    }

    @Test
    @DisplayName("carries every supplied criterion")
    void carriesCriteria() {
        Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant to = Instant.now();

        TransactionFilter filter = TransactionFilter.builder()
                .type(TransactionType.DEPOSIT)
                .from(from)
                .to(to)
                .limit(10)
                .build();

        assertEquals(TransactionType.DEPOSIT, filter.getType().orElseThrow());
        assertEquals(from, filter.getFrom().orElseThrow());
        assertEquals(to, filter.getTo().orElseThrow());
        assertEquals(10, filter.getLimit());
        assertTrue(filter.toString().contains("DEPOSIT"));
    }

    @Test
    @DisplayName("supports an unlimited result set")
    void supportsUnlimited() {
        TransactionFilter filter = TransactionFilter.builder().unlimited().build();

        assertFalse(filter.hasLimit());
        assertEquals(0, filter.getLimit());
    }

    @Test
    @DisplayName("rejects an inverted date range at construction time")
    void rejectsInvertedRange() {
        Instant now = Instant.now();

        assertThrows(IllegalArgumentException.class, () -> TransactionFilter.builder()
                .from(now)
                .to(now.minus(1, ChronoUnit.DAYS))
                .build());
    }

    @Test
    @DisplayName("accepts a one-sided date bound")
    void acceptsOneSidedBounds() {
        Instant now = Instant.now();

        assertTrue(TransactionFilter.builder().from(now).build().getTo().isEmpty());
        assertTrue(TransactionFilter.builder().to(now).build().getFrom().isEmpty());
    }
}
