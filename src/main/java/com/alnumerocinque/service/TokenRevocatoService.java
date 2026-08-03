package com.alnumerocinque.service;

import com.alnumerocinque.domain.TokenRevocato;
import com.alnumerocinque.repository.TokenRevocatoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Blacklist di logout per JWT stateless (vedi docs/07-auth.md). Il jti di un
 * token revocato resta in blacklist finche' il token non sarebbe comunque
 * scaduto naturalmente; pulisciScaduti rimuove periodicamente le righe non
 * piu' necessarie, cosi' che la tabella non cresca indefinitamente.
 */
@Service
public class TokenRevocatoService {

    private final TokenRevocatoRepository tokenRevocatoRepository;

    public TokenRevocatoService(TokenRevocatoRepository tokenRevocatoRepository) {
        this.tokenRevocatoRepository = tokenRevocatoRepository;
    }

    @Transactional
    public void revoca(String jti, OffsetDateTime scadeAt) {
        if (!tokenRevocatoRepository.existsById(jti)) {
            tokenRevocatoRepository.save(new TokenRevocato(jti, scadeAt));
        }
    }

    @Transactional(readOnly = true)
    public boolean isRevocato(String jti) {
        return tokenRevocatoRepository.existsById(jti);
    }

    @Scheduled(fixedDelayString = "${app.token-revocato.pulizia-intervallo-ms:3600000}")
    @Transactional
    public void pulisciScaduti() {
        tokenRevocatoRepository.deleteByScadeAtBefore(OffsetDateTime.now());
    }
}
