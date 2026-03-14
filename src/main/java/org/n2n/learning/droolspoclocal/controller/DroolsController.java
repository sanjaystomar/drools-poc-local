package com.poc.drools.controller;

import com.poc.drools.model.LoanApplication;
import com.poc.drools.model.Order;
import com.poc.drools.service.DroolsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DroolsController {

    private final DroolsService droolsService;

    public DroolsController(DroolsService droolsService) {
        this.droolsService = droolsService;
    }

    /**
     * POST /api/loan/evaluate
     * Evaluates a loan application using Drools rules.
     *
     * Example request body:
     * {
     *   "applicantName": "John Doe",
     *   "age": 30,
     *   "annualIncome": 80000,
     *   "creditScore": 720,
     *   "requestedAmount": 200000
     * }
     */
    @PostMapping("/loan/evaluate")
    public ResponseEntity<LoanApplication> evaluateLoan(@RequestBody LoanApplication application) {
        LoanApplication result = droolsService.evaluateLoan(application);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/order/discount
     * Applies discount rules to an order.
     *
     * Example request body:
     * {
     *   "customerId": "CUST001",
     *   "customerType": "VIP",
     *   "orderAmount": 1500,
     *   "itemCount": 12
     * }
     */
    @PostMapping("/order/discount")
    public ResponseEntity<Order> applyDiscount(@RequestBody Order order) {
        Order result = droolsService.applyDiscount(order);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Drools POC is running!");
    }
}
