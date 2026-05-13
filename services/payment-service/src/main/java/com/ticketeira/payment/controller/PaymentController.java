package com.ticketeira.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @GetMapping("/me")
    public ResponseEntity<List<Object>> myPayments() {
        return ResponseEntity.ok(List.of());
    }
}
