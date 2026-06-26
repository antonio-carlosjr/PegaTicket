package com.ticketeira.user.dto;

import com.ticketeira.user.domain.Papel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 120) String nome,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(min = 6, max = 72) String senha,

        /** Papel desejado. Se omitido, assume PARTICIPANTE. ADMIN nao pode ser auto-atribuido. */
        Papel papel,

        /** Obrigatorio quando papel = PROMOTOR. Formato 000.000.000-00. */
        @Pattern(regexp = "^(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})?$",
                message = "CPF deve estar no formato 000.000.000-00")
        String cpf,

        /** Obrigatorio quando papel = PROMOTOR. Formato (00) 00000-0000. */
        @Pattern(regexp = "^(\\(\\d{2}\\) \\d{4,5}-\\d{4})?$",
                message = "Telefone deve estar no formato (00) 00000-0000")
        String telefone,

        @Email @Size(max = 160) String emailContato,
        @Pattern(regexp = "^(\\d{5}-\\d{3})?$", message = "CEP invalido") String cep,
        @Size(max = 160) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 80) String complemento,
        @Size(max = 80) String bairro,
        @Size(max = 80) String cidade,
        @Pattern(regexp = "^([A-Z]{2})?$", message = "UF invalida") String uf,
        @Size(max = 80) String instagram,
        @Size(max = 200) String website
) {
    public Papel papelOrDefault() {
        return papel != null ? papel : Papel.PARTICIPANTE;
    }
}
