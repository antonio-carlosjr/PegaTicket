package com.ticketeira.user.controller;

import com.ticketeira.common.exception.UnauthorizedException;
import com.ticketeira.user.dto.UsuarioResponse;
import com.ticketeira.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> me(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("Header X-User-Id ausente (acesso direto sem gateway nao permitido).");
        }
        return ResponseEntity.ok(userService.findById(userId));
    }

    @PutMapping("/{id}/verify")
    public ResponseEntity<UsuarioResponse> verify(@PathVariable Long id) {
        // TODO: restringir a admin em sprint futura
        return ResponseEntity.ok(userService.verificar(id));
    }
}
