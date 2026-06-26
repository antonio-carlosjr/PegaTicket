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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ticketeira.user.domain.Papel;
import com.ticketeira.user.dto.UsuarioResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public Page<UsuarioResponse> listarTodos(Boolean ativo, Boolean verificado, Papel papel, String busca, Pageable pageable) {
        Page<Usuario> page = usuarios.findComFiltros(ativo, verificado, papel, busca, pageable);
        List<Long> ids = page.getContent().stream().map(Usuario::getId).toList();
        // Status do perfil de promotor por usuario (1 query batch, sem N+1).
        Map<Long, String> statusPorUsuario = ids.isEmpty() ? Map.of()
                : perfis.findByUsuarioIdIn(ids).stream()
                        .collect(Collectors.toMap(PerfilVerificado::getUsuarioId, p -> p.getStatus().name()));
        return page.map(u -> UsuarioResponse.from(u, statusPorUsuario.get(u.getId())));
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
