-- V1__create_drools_rules_table.sql
-- Creates the drools_rules table and seeds it with the two initial rule sets.

CREATE TABLE drools_rules (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name   VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    drl_content TEXT         NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Seed: Loan Approval Rules
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO drools_rules (rule_name, description, drl_content, active) VALUES (
'loan-approval',
'Evaluates loan applications by credit score, age, and income',
'package com.poc.drools.rules;

import com.poc.drools.model.LoanApplication;

rule "Reject Low Credit Score"
    salience 100
    when
        $loan: LoanApplication(creditScore < 600, !approved)
    then
        $loan.setApproved(false);
        $loan.setStatus("REJECTED");
        $loan.setReason("Credit score below minimum threshold of 600");
        update($loan);
end

rule "Reject Underage Applicant"
    salience 100
    when
        $loan: LoanApplication(age < 18, !approved)
    then
        $loan.setApproved(false);
        $loan.setStatus("REJECTED");
        $loan.setReason("Applicant must be at least 18 years old");
        update($loan);
end

rule "Reject Insufficient Income"
    salience 90
    when
        $loan: LoanApplication(
            creditScore >= 600,
            age >= 18,
            requestedAmount > annualIncome * 5,
            !approved
        )
    then
        $loan.setApproved(false);
        $loan.setStatus("REJECTED");
        $loan.setReason("Requested amount exceeds 5x annual income limit");
        update($loan);
end

rule "Approve Full Amount - Excellent Credit"
    salience 80
    when
        $loan: LoanApplication(
            creditScore >= 750,
            age >= 18,
            annualIncome >= 50000,
            requestedAmount <= annualIncome * 5,
            status == null
        )
    then
        $loan.setApproved(true);
        $loan.setStatus("APPROVED");
        $loan.setApprovedAmount($loan.getRequestedAmount());
        $loan.setReason("Excellent credit profile - full amount approved");
        update($loan);
end

rule "Approve Partial Amount - Good Credit"
    salience 70
    when
        $loan: LoanApplication(
            creditScore >= 650,
            creditScore < 750,
            age >= 18,
            annualIncome >= 30000,
            requestedAmount <= annualIncome * 5,
            status == null
        )
    then
        $loan.setApproved(true);
        $loan.setStatus("CONDITIONALLY_APPROVED");
        $loan.setApprovedAmount($loan.getRequestedAmount() * 0.75);
        $loan.setReason("Good credit - approved 75% of requested amount");
        update($loan);
end

rule "Default Rejection"
    salience 1
    when
        $loan: LoanApplication(status == null)
    then
        $loan.setApproved(false);
        $loan.setStatus("REJECTED");
        $loan.setReason("Does not meet minimum eligibility criteria");
        update($loan);
end',
TRUE
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Seed: Discount Rules
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO drools_rules (rule_name, description, drl_content, active) VALUES (
'discount',
'Applies tiered discounts based on customer type, order size, and amount',
'package com.poc.drools.rules;

import com.poc.drools.model.Order;

rule "VIP Customer Discount"
    salience 100
    when
        $order: Order(customerType == "VIP")
    then
        $order.setDiscountPercentage(20.0);
        $order.setDiscountReason("VIP customer - 20% discount");
        update($order);
end

rule "Premium Customer Discount"
    salience 90
    when
        $order: Order(customerType == "PREMIUM", discountPercentage == 0.0)
    then
        $order.setDiscountPercentage(10.0);
        $order.setDiscountReason("Premium customer - 10% discount");
        update($order);
end

rule "Bulk Order Bonus Discount"
    salience 80
    when
        $order: Order(itemCount >= 10, orderAmount >= 500)
    then
        double current = $order.getDiscountPercentage();
        $order.setDiscountPercentage(current + 5.0);
        $order.setDiscountReason($order.getDiscountReason() != null
            ? $order.getDiscountReason() + " + Bulk order bonus 5%"
            : "Bulk order - 5% discount");
        update($order);
end

rule "High Value Order Discount"
    salience 70
    when
        $order: Order(orderAmount >= 1000, customerType == "REGULAR")
    then
        $order.setDiscountPercentage(7.0);
        $order.setDiscountReason("High-value order - 7% discount");
        update($order);
end

rule "Calculate Final Amount"
    salience 1
    when
        $order: Order(finalAmount == 0.0)
    then
        double discount = $order.getDiscountPercentage();
        double finalAmt = $order.getOrderAmount() * (1 - discount / 100);
        $order.setFinalAmount(finalAmt);
        if ($order.getDiscountReason() == null) {
            $order.setDiscountReason("No discount applicable");
        }
        update($order);
end',
TRUE
);
