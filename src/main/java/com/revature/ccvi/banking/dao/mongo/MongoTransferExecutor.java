package com.revature.ccvi.banking.dao.mongo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.bson.Document;

import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.revature.ccvi.banking.config.MongoConnectionManager;
import com.revature.ccvi.banking.dao.TransferExecutor;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.TransactionType;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Atomic transfer for MongoDB.
 *
 * <p>On a replica set or sharded cluster the four writes run inside one multi document
 * transaction. On a standalone server (where transactions are unavailable) the executor performs
 * a guarded debit first and compensates by refunding the source if the credit cannot be applied,
 * so money is never lost.</p>
 */
public class MongoTransferExecutor implements TransferExecutor {

    private static final TransactionOptions TRANSACTION_OPTIONS = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY)
            .readPreference(ReadPreference.primary())
            .build();

    private final MongoClient client;
    private final MongoCollection<Document> accounts;
    private final MongoCollection<Document> transactions;
    private final boolean transactionsSupported;

    public MongoTransferExecutor(MongoConnectionManager connectionManager) {
        this(connectionManager.getClient(), connectionManager.getDatabase(),
                connectionManager.isTransactionsSupported());
    }

    public MongoTransferExecutor(MongoClient client, MongoDatabase database, boolean transactionsSupported) {
        this.client = client;
        this.accounts = database.getCollection(MongoConnectionManager.ACCOUNTS);
        this.transactions = database.getCollection(MongoConnectionManager.TRANSACTIONS);
        this.transactionsSupported = transactionsSupported;
    }

    @Override
    public TransferOutcome transfer(String sourceAccountId, String destinationAccountId, BigDecimal amount,
                                    BigDecimal minimumSourceBalance, String description) {
        BigDecimal transferAmount = MoneyUtil.normalize(amount);
        BigDecimal floor = MoneyUtil.normalize(
                minimumSourceBalance == null ? MoneyUtil.zero() : minimumSourceBalance);
        return transactionsSupported
                ? transferInSession(sourceAccountId, destinationAccountId, transferAmount, floor, description)
                : transferWithCompensation(sourceAccountId, destinationAccountId, transferAmount, floor, description);
    }

    private TransferOutcome transferInSession(String sourceAccountId, String destinationAccountId,
                                              BigDecimal amount, BigDecimal floor, String description) {
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> {
                BigDecimal newSourceBalance = debit(session, sourceAccountId, amount, floor);
                if (newSourceBalance == null) {
                    session.abortTransaction();
                    return TransferOutcome.rejected();
                }
                BigDecimal newDestinationBalance = credit(session, destinationAccountId, amount);
                if (newDestinationBalance == null) {
                    session.abortTransaction();
                    throw new DataAccessException("The destination account no longer exists; transfer aborted.");
                }
                Instant now = Instant.now();
                transactions.insertMany(session, List.of(
                        ledgerEntry(sourceAccountId, destinationAccountId, TransactionType.TRANSFER_OUT,
                                amount, newSourceBalance, description, now),
                        ledgerEntry(destinationAccountId, sourceAccountId, TransactionType.TRANSFER_IN,
                                amount, newDestinationBalance, description, now)));
                return TransferOutcome.applied(newSourceBalance, newDestinationBalance);
            }, TRANSACTION_OPTIONS);
        } catch (MongoException ex) {
            throw new DataAccessException("Transfer failed and was rolled back: " + ex.getMessage(), ex);
        }
    }

    private TransferOutcome transferWithCompensation(String sourceAccountId, String destinationAccountId,
                                                     BigDecimal amount, BigDecimal floor, String description) {
        BigDecimal newSourceBalance = debit(null, sourceAccountId, amount, floor);
        if (newSourceBalance == null) {
            return TransferOutcome.rejected();
        }
        try {
            BigDecimal newDestinationBalance = credit(null, destinationAccountId, amount);
            if (newDestinationBalance == null) {
                credit(null, sourceAccountId, amount);
                throw new DataAccessException("The destination account no longer exists; the debit was reversed.");
            }
            Instant now = Instant.now();
            transactions.insertMany(List.of(
                    ledgerEntry(sourceAccountId, destinationAccountId, TransactionType.TRANSFER_OUT,
                            amount, newSourceBalance, description, now),
                    ledgerEntry(destinationAccountId, sourceAccountId, TransactionType.TRANSFER_IN,
                            amount, newDestinationBalance, description, now)));
            return TransferOutcome.applied(newSourceBalance, newDestinationBalance);
        } catch (MongoException ex) {
            credit(null, sourceAccountId, amount);
            throw new DataAccessException("Transfer failed; the debit was reversed: " + ex.getMessage(), ex);
        }
    }

    private BigDecimal debit(ClientSession session, String accountId, BigDecimal amount, BigDecimal floor) {
        BigDecimal requiredBalance = MoneyUtil.normalize(floor.add(amount));
        var filter = Filters.and(Filters.eq("_id", accountId),
                Filters.gte("balance", MongoDocumentMapper.toDecimal(requiredBalance)));
        var update = Updates.inc("balance", MongoDocumentMapper.toDecimal(amount.negate()));
        var options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
        Document updated = session == null
                ? accounts.findOneAndUpdate(filter, update, options)
                : accounts.findOneAndUpdate(session, filter, update, options);
        return updated == null ? null : MongoDocumentMapper.toBigDecimal(updated.get("balance"));
    }

    private BigDecimal credit(ClientSession session, String accountId, BigDecimal amount) {
        var filter = Filters.eq("_id", accountId);
        var update = Updates.inc("balance", MongoDocumentMapper.toDecimal(amount));
        var options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
        Document updated = session == null
                ? accounts.findOneAndUpdate(filter, update, options)
                : accounts.findOneAndUpdate(session, filter, update, options);
        return updated == null ? null : MongoDocumentMapper.toBigDecimal(updated.get("balance"));
    }

    private Document ledgerEntry(String accountId, String counterpartyId, TransactionType type, BigDecimal amount,
                                 BigDecimal resultingBalance, String description, Instant createdAt) {
        return new Document("_id", UUID.randomUUID().toString())
                .append("accountId", accountId)
                .append("counterpartyAccountId", counterpartyId)
                .append("transactionType", type.name())
                .append("amount", MongoDocumentMapper.toDecimal(amount))
                .append("resultingBalance", MongoDocumentMapper.toDecimal(resultingBalance))
                .append("description", description)
                .append("createdAt", MongoDocumentMapper.toDate(createdAt));
    }
}
