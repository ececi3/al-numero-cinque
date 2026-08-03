package com.alnumerocinque.config;

import com.alnumerocinque.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Autenticazione stateless via JWT (vedi docs/07-auth.md):
 * - /api/auth/login pubblico (unico endpoint di /api/auth/** che lo e';
 *   /api/auth/cambia-password richiede comunque un utente autenticato)
 * - /api/sessioni/**, /api/comande/** riservati al ruolo CAMERIERE
 * - /api/kds/** riservato al ruolo CUCINA
 * - /api/admin/** riservato al ruolo ADMIN
 * - /ws-kds/** (handshake WebSocket del KDS) e' permitAll qui: l'autenticazione
 *   e per-ruolo (CUCINA) e' delegata a KdsHandshakeAuthInterceptor, l'unico
 *   modo per leggere il JWT anche quando arriva come query param invece che
 *   header (client WebSocket nativi da browser non possono impostare header
 *   custom sull'handshake)
 * - tutto il resto richiede comunque autenticazione (nessuna rotta
 *   permitAll residua, a differenza del placeholder precedente)
 *
 * Nessuna sessione server-side (SessionCreationPolicy.STATELESS): ogni
 * richiesta porta il proprio JWT, coerente con dispositivi che possono
 * riconnettersi in modo intermittente.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/sessioni/**", "/api/comande/**").hasRole("CAMERIERE")
                        .requestMatchers("/api/kds/**").hasRole("CUCINA")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/ws-kds/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
