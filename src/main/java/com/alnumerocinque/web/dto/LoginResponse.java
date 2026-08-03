package com.alnumerocinque.web.dto;

public record LoginResponse(
        String token,
        String username,
        String ruolo
) {
}
