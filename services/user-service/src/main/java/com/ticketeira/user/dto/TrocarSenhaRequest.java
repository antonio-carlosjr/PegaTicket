package com.ticketeira.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Troca de senha do proprio usuario autenticado (PUT /users/me/senha). */
public record TrocarSenhaRequest(
        @NotBlank(message = "Informe a senha atual.") String senhaAtual,
        @NotBlank @Size(min = 6, max = 72, message = "A nova senha deve ter entre 6 e 72 caracteres.") String novaSenha
) {}
