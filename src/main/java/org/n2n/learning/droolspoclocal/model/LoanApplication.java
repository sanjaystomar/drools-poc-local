package org.n2n.learning.droolspoclocal.model;

import com.fasterxml.jackson.annotation.JacksonAnnotation;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
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
