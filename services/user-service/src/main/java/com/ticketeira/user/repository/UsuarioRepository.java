package com.ticketeira.user.repository;

import com.ticketeira.user.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketeira.user.domain.Papel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM Usuario u WHERE " +
           "(:ativo IS NULL OR u.ativo = :ativo) AND " +
           "(:verificado IS NULL OR u.verificado = :verificado) AND " +
           "(:papel IS NULL OR u.papel = :papel) AND " +
           "(:busca IS NULL OR LOWER(u.nome) LIKE LOWER(CONCAT('%', :busca, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :busca, '%')))")
    Page<Usuario> findComFiltros(
            @Param("ativo") Boolean ativo,
            @Param("verificado") Boolean verificado,
            @Param("papel") Papel papel,
            @Param("busca") String busca,
            Pageable pageable
    );
}
