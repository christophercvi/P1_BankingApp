package com.revature.ccvi.banking.ui;

import com.revature.ccvi.banking.model.Customer;

/**
 * Holds the currently signed-in customer for the life of the console session.
 */
class Session {

    private Customer customer;

    boolean isAuthenticated() {
        return customer != null;
    }

    Customer getCustomer() {
        return customer;
    }

    String getCustomerId() {
        return customer == null ? null : customer.getId();
    }

    void login(Customer customer) {
        this.customer = customer;
    }

    void logout() {
        this.customer = null;
    }
}
