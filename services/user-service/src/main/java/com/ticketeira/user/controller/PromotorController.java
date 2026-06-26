package com.ticketeira.user.controller;

import com.ticketeira.common.exception.UnauthorizedException;
import com.ticketeira.user.dto.RegisterRequest;
import com.ticketeira.user.service.PromotorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/promotores")
public class PromotorController {

    private final PromotorService promotorService;

    public PromotorController(PromotorService promotorService) {
        this.promotorService = promotorService;
    }

    @PostMapping("/solicitar")
    public ResponseEntity<Void> solicitar(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @Valid @RequestBody RegisterRequest req) {
        if (userId == null) {
            throw new UnauthorizedException("Header X-User-Id ausente.");
        }
        promotorService.solicitarOuReenviar(userId, req);
        return ResponseEntity.ok().build();
    }
}
