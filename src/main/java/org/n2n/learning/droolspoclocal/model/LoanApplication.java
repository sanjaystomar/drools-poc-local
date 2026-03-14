package com.poc.drools.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplication {

    private String applicantName;
    private int age;
    private double annualIncome;
    private int creditScore;
    private double requestedAmount;

    // Fields set by Drools rules
    private boolean approved;
    private String status;
    private String reason;
    private double approvedAmount;
}
