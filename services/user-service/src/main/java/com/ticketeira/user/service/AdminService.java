package com.ticketeira.user.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.NotFoundException;
import com.ticketeira.user.domain.PerfilVerificado;
import com.ticketeira.user.domain.StatusVerificacao;
import com.ticketeira.user.domain.Usuario;
import com.ticketeira.user.repository.PerfilVerificadoRepository;
import com.ticketeira.user.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UsuarioRepository usuarios;
    private final PerfilVerificadoRepository perfis;
    private final PromotorStatusEmailService emailService;

    public AdminService(UsuarioRepository usuarios,
                        PerfilVerificadoRepository perfis,
                        PromotorStatusEmailService emailService) {
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public java.util.List<com.ticketeira.user.dto.UsuarioResponse> listarTodos() {
        return usuarios.findAll().stream().map(com.ticketeira.user.dto.UsuarioResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public com.ticketeira.user.dto.UsuarioDetalheResponse detalhar(Long id) {
        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        PerfilVerificado perfil = perfis.findByUsuarioId(id).orElse(null);
        return com.ticketeira.user.dto.UsuarioDetalheResponse.from(usuario, perfil);
    }

    @Transactional
    public void aprovarPromotor(Long id) {
        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        PerfilVerificado perfil = perfis.findByUsuarioId(id)
                .orElseThrow(() -> new BusinessException("Perfil de promotor não encontrado para este usuário", 404));

        if (perfil.getStatus() == StatusVerificacao.VERIFICADO) {
            return; // Idempotente
        }

        perfil.aprovar();
        usuario.marcarComoVerificado();
        usuario.promover();

        usuarios.save(usuario);
        perfis.save(perfil);

        emailService.enviarAprovacao(usuario);
    }

    @Transactional
    public void rejeitarPromotor(Long id, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("Motivo da rejeição é obrigatório", 400);
        }

        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        PerfilVerificado perfil = perfis.findByUsuarioId(id)
                .orElseThrow(() -> new BusinessException("Perfil de promotor não encontrado para este usuário", 404));

        perfil.rejeitar(motivo);
        perfis.save(perfil);

        emailService.enviarRejeicao(usuario, motivo);
    }

    @Transactional
    public void ativarUsuario(Long id) {
        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        usuario.ativar();
        usuarios.save(usuario);
    }

    @Transactional
    public void inativarUsuario(Long id) {
        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        
        // Evita inativar a conta seed
        if ("ADMIN".equals(usuario.getPapel().name()) && "admin@pegaticket.local".equals(usuario.getEmail())) {
            throw new BusinessException("Você não pode inativar a conta seed de administrador.", 403);
        }
        
        usuario.inativar();
        usuarios.save(usuario);
    }
}
