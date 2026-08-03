package com.alnumerocinque.security;

import com.alnumerocinque.domain.RuoloUtente;

/**
 * Principal autenticato, ricostruito direttamente dalle claim del JWT
 * (nessuna query al DB a ogni richiesta). Usato dai controller per
 * recuperare l'id dell'utente autenticato invece di fidarsi di un
 * cameriereId inviato dal client. {@code jti} identifica il token corrente
 * ed e' usato da AuthController.logout per revocarlo (vedi
 * TokenRevocatoService).
 */
public record AuthenticatedUser(Long id, String username, RuoloUtente ruolo, String jti) {
}
