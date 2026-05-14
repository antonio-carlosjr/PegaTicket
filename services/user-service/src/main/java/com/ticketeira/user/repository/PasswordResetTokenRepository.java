package com.ticketeira.user.repository;

import com.ticketeira.user.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usadoEm = :agora WHERE t.usuarioId = :usuarioId AND t.usadoEm IS NULL")
    int invalidarTokensAtivosDoUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") OffsetDateTime agora);
}
