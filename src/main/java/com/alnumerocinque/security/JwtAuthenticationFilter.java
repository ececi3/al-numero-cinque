package com.alnumerocinque.security;

import com.alnumerocinque.domain.RuoloUtente;
import com.alnumerocinque.service.TokenRevocatoService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Estrae il JWT dall'header Authorization (Bearer), lo valida e popola il
 * SecurityContext con un {@link AuthenticatedUser} come principal. Se
 * l'header manca, il token non e' valido/scaduto, o il suo jti risulta
 * revocato (logout esplicito, vedi TokenRevocatoService), il filtro non
 * solleva eccezioni: lascia semplicemente il contesto anonimo, cosi' che sia
 * Spring Security (authorizeHttpRequests) a rispondere 401/403 in modo
 * uniforme sugli endpoint protetti.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFISSO_BEARER = "Bearer ";

    private final JwtService jwtService;
    private final TokenRevocatoService tokenRevocatoService;

    public JwtAuthenticationFilter(JwtService jwtService, TokenRevocatoService tokenRevocatoService) {
        this.jwtService = jwtService;
        this.tokenRevocatoService = tokenRevocatoService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(PREFISSO_BEARER)) {
            try {
                Claims claims = jwtService.valida(header.substring(PREFISSO_BEARER.length())).getPayload();

                if (!tokenRevocatoService.isRevocato(claims.getId())) {
                    AuthenticatedUser utenteAutenticato = new AuthenticatedUser(
                            claims.get("utenteId", Long.class),
                            claims.getSubject(),
                            RuoloUtente.valueOf(claims.get("ruolo", String.class)),
                            claims.getId());

                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + utenteAutenticato.ruolo().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(utenteAutenticato, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
