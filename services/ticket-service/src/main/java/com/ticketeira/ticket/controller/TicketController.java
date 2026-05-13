package com.ticketeira.ticket.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    @GetMapping("/me")
    public ResponseEntity<List<Object>> myTickets() {
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/inscricoes")
    public ResponseEntity<Object> inscrever() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("message", "Fluxo de inscricao sera implementado na Sprint 1."));
    }
}
