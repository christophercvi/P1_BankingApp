package com.revature.ccvi.banking.dao.mongo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.revature.ccvi.banking.config.MongoConnectionManager;
import com.revature.ccvi.banking.dao.CustomerDao;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.Customer;

/**
 * MongoDB implementation of {@link CustomerDao}. Uniqueness is enforced by unique indexes and
 * surfaced as a friendly message when the driver reports a duplicate key error.
 */
public class MongoCustomerDao implements CustomerDao {

    private static final int DUPLICATE_KEY = 11000;

    private final MongoCollection<Document> collection;

    public MongoCustomerDao(MongoDatabase database) {
        this.collection = database.getCollection(MongoConnectionManager.CUSTOMERS);
    }

    @Override
    public Customer create(Customer customer) {
        if (customer.getId() == null || customer.getId().isBlank()) {
            customer.setId(UUID.randomUUID().toString());
        }
        if (customer.getCreatedAt() == null) {
            customer.setCreatedAt(Instant.now());
        }
        try {
            collection.insertOne(MongoDocumentMapper.toDocument(customer));
            return customer;
        } catch (MongoWriteException ex) {
            if (ex.getError().getCode() == DUPLICATE_KEY) {
                throw new DataAccessException("A customer with that username or email already exists.", ex);
            }
            throw new DataAccessException("Failed to create customer: " + ex.getMessage(), ex);
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to create customer: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        return findOne(Filters.eq("_id", customerId));
    }

    @Override
    public Optional<Customer> findByUsername(String username) {
        return findOne(Filters.eq("username", username));
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        return findOne(Filters.eq("email", email));
    }

    @Override
    public List<Customer> findAll() {
        try {
            List<Customer> customers = new ArrayList<>();
            collection.find().sort(Sorts.ascending("createdAt"))
                    .forEach(document -> customers.add(MongoDocumentMapper.toCustomer(document)));
            return customers;
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to list customers: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean update(Customer customer) {
        try {
            return collection.updateOne(Filters.eq("_id", customer.getId()),
                    Updates.combine(
                            Updates.set("email", customer.getEmail()),
                            Updates.set("firstName", customer.getFirstName()),
                            Updates.set("lastName", customer.getLastName()),
                            Updates.set("phone", customer.getPhone())))
                    .getMatchedCount() == 1;
        } catch (MongoWriteException ex) {
            if (ex.getError().getCode() == DUPLICATE_KEY) {
                throw new DataAccessException("That email is already registered to another customer.", ex);
            }
            throw new DataAccessException("Failed to update customer profile: " + ex.getMessage(), ex);
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to update customer profile: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean updatePasswordHash(String customerId, String passwordHash) {
        try {
            return collection.updateOne(Filters.eq("_id", customerId),
                    Updates.set("passwordHash", passwordHash)).getMatchedCount() == 1;
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to update password: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean touchLastLogin(String customerId, Instant loginTime) {
        try {
            return collection.updateOne(Filters.eq("_id", customerId),
                    Updates.set("lastLoginAt", MongoDocumentMapper.toDate(loginTime))).getMatchedCount() == 1;
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to record login time: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean deleteById(String customerId) {
        try {
            return collection.deleteOne(Filters.eq("_id", customerId)).getDeletedCount() == 1;
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to delete customer: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        return count(Filters.eq("username", username)) > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        return count(Filters.eq("email", email)) > 0;
    }

    private long count(org.bson.conversions.Bson filter) {
        try {
            return collection.countDocuments(filter);
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to check customer uniqueness: " + ex.getMessage(), ex);
        }
    }

    private Optional<Customer> findOne(org.bson.conversions.Bson filter) {
        try {
            return Optional.ofNullable(collection.find(filter).first()).map(MongoDocumentMapper::toCustomer);
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to load customer: " + ex.getMessage(), ex);
        }
    }
}
