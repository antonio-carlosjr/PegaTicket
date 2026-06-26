package com.ticketeira.user.controller;

import com.ticketeira.common.exception.UnauthorizedException;
import com.ticketeira.user.dto.RejeicaoRequest;
import com.ticketeira.user.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    private void requireAdmin(String papel) {
        if (!"ADMIN".equals(papel)) {
            throw new UnauthorizedException("Acesso restrito a administradores.");
        }
    }

    @org.springframework.web.bind.annotation.GetMapping
    public ResponseEntity<java.util.List<com.ticketeira.user.dto.UsuarioResponse>> listar(@RequestHeader("X-User-Papel") String papel) {
        requireAdmin(papel);
        return ResponseEntity.ok(adminService.listarTodos());
    }

    @org.springframework.web.bind.annotation.GetMapping("/{id}")
    public ResponseEntity<com.ticketeira.user.dto.UsuarioDetalheResponse> detalhar(@PathVariable Long id, @RequestHeader("X-User-Papel") String papel) {
        requireAdmin(papel);
        return ResponseEntity.ok(adminService.detalhar(id));
    }

    @PutMapping("/{id}/aprovar")
    public ResponseEntity<Void> aprovarPromotor(@PathVariable Long id, @RequestHeader("X-User-Papel") String papel) {
        requireAdmin(papel);
        adminService.aprovarPromotor(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/rejeitar")
    public ResponseEntity<Void> rejeitarPromotor(@PathVariable Long id, @RequestBody RejeicaoRequest req, @RequestHeader("X-User-Papel") String papel) {
        requireAdmin(papel);
        adminService.rejeitarPromotor(id, req.motivo());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/ativar")
    public ResponseEntity<Void> ativarUsuario(@PathVariable Long id, @RequestHeader("X-User-Papel") String papel) {
        requireAdmin(papel);
        adminService.ativarUsuario(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/inativar")
    public ResponseEntity<Void> inativarUsuario(@PathVariable Long id, @RequestHeader("X-User-Papel") String papel) {
        requireAdmin(papel);
        adminService.inativarUsuario(id);
        return ResponseEntity.ok().build();
    }
}
