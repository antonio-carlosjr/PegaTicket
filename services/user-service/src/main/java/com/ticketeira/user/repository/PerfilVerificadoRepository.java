package com.ticketeira.user.repository;

import com.ticketeira.user.domain.PerfilVerificado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PerfilVerificadoRepository extends JpaRepository<PerfilVerificado, Long> {
    Optional<PerfilVerificado> findByUsuarioId(Long usuarioId);
    List<PerfilVerificado> findByUsuarioIdIn(Collection<Long> usuarioIds);
    boolean existsByCpf(String cpf);
}
