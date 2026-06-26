package com.ticketeira.user.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.NotFoundException;
import com.ticketeira.user.domain.PerfilVerificado;
import com.ticketeira.user.domain.Usuario;
import com.ticketeira.user.dto.AtualizarPerfilRequest;
import com.ticketeira.user.dto.TrocarSenhaRequest;
import com.ticketeira.user.dto.UsuarioDetalheResponse;
import com.ticketeira.user.dto.UsuarioResponse;
import com.ticketeira.user.repository.PerfilVerificadoRepository;
import com.ticketeira.user.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UsuarioRepository repository;
    private final PerfilVerificadoRepository perfis;
    private final PasswordEncoder passwordEncoder;

    public UserService(UsuarioRepository repository,
                       PerfilVerificadoRepository perfis,
                       PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.perfis = perfis;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UsuarioResponse findById(Long id) {
        Usuario u = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado."));
        return UsuarioResponse.from(u);
    }

    /** Perfil completo do proprio usuario (inclui perfil rico, se promotor). */
    @Transactional(readOnly = true)
    public UsuarioDetalheResponse perfilProprio(Long id) {
        Usuario u = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado."));
        PerfilVerificado perfil = perfis.findByUsuarioId(id).orElse(null);
        return UsuarioDetalheResponse.from(u, perfil);
    }

    /**
     * Atualiza o proprio perfil. `nome` vale para todos; os campos do perfil rico
     * so se aplicam a quem ja tem perfil (promotores). telefone/cpf (NOT NULL) mantem
     * o valor atual se o request os omitir.
     */
    @Transactional
    public UsuarioDetalheResponse atualizarPerfil(Long id, AtualizarPerfilRequest req) {
        Usuario u = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado."));
        u.atualizarNome(req.nome());
        repository.save(u);

        PerfilVerificado perfil = perfis.findByUsuarioId(id).orElse(null);
        if (perfil != null) {
            perfil.atualizar(
                    coalesce(req.telefone(), perfil.getTelefone()),
                    coalesce(req.cpf(), perfil.getCpf()),
                    req.emailContato(), req.cep(), req.logradouro(), req.numero(),
                    req.complemento(), req.bairro(), req.cidade(), req.uf(),
                    req.instagram(), req.website()
            );
            perfis.save(perfil);
        }
        return UsuarioDetalheResponse.from(u, perfil);
    }

    /** Troca a senha do proprio usuario, exigindo a senha atual correta. */
    @Transactional
    public void trocarSenha(Long id, TrocarSenhaRequest req) {
        Usuario u = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado."));
        if (!passwordEncoder.matches(req.senhaAtual(), u.getSenhaHash())) {
            throw new BusinessException("Senha atual incorreta.", 400);
        }
        u.atualizarSenha(passwordEncoder.encode(req.novaSenha()));
        repository.save(u);
    }

    @Transactional
    public UsuarioResponse verificar(Long id) {
        Usuario u = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado."));
        u.marcarComoVerificado();
        return UsuarioResponse.from(repository.save(u));
    }

    private static String coalesce(String novo, String atual) {
        return (novo != null && !novo.isBlank()) ? novo : atual;
    }
}
