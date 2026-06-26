package com.ticketeira.user.controller;

import com.ticketeira.common.exception.UnauthorizedException;
import com.ticketeira.user.dto.AtualizarPerfilRequest;
import com.ticketeira.user.dto.TrocarSenhaRequest;
import com.ticketeira.user.dto.UsuarioDetalheResponse;
import com.ticketeira.user.dto.UsuarioResponse;
import com.ticketeira.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("Header X-User-Id ausente (acesso direto sem gateway nao permitido).");
        }
        return userId;
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> me(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(userService.findById(requireUserId(userId)));
    }

    /** Perfil completo do proprio usuario (com perfil rico, se promotor). */
    @GetMapping("/me/perfil")
    public ResponseEntity<UsuarioDetalheResponse> meuPerfil(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(userService.perfilProprio(requireUserId(userId)));
    }

    /** Atualiza o proprio perfil (nome + perfil rico de promotor). */
    @PutMapping("/me")
    public ResponseEntity<UsuarioDetalheResponse> atualizarPerfil(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody AtualizarPerfilRequest req) {
        return ResponseEntity.ok(userService.atualizarPerfil(requireUserId(userId), req));
    }

    /** Troca a senha do proprio usuario (exige a senha atual). */
    @PutMapping("/me/senha")
    public ResponseEntity<Void> trocarSenha(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody TrocarSenhaRequest req) {
        userService.trocarSenha(requireUserId(userId), req);
        return ResponseEntity.noContent().build();
    }
}
