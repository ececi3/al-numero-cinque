package com.alnumerocinque.service;

import com.alnumerocinque.repository.ComandaRepository;
import com.alnumerocinque.repository.GruppoInvioRepository;
import com.alnumerocinque.repository.GruppoInvioRepository.TempoPreparazioneProiezione;
import com.alnumerocinque.repository.SessioneRepository;
import com.alnumerocinque.web.dto.AnalyticsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Analytics per l'admin: coperti totali, comande totali, tempi medi di
 * preparazione per portata (numeroPortata). Il tempo di preparazione di un
 * gruppo e' inCodaAt -> prontoAt (dal momento del fire in coda cucina al
 * momento in cui la cucina lo segna pronto), calcolato solo sui gruppi che
 * sono effettivamente passati dalla coda (esclude quelli "senza cucina",
 * vedi CoursingService.fireProssimoTrattenuto).
 */
@Service
public class AnalyticsService {

    private final SessioneRepository sessioneRepository;
    private final ComandaRepository comandaRepository;
    private final GruppoInvioRepository gruppoInvioRepository;

    public AnalyticsService(SessioneRepository sessioneRepository,
                             ComandaRepository comandaRepository,
                             GruppoInvioRepository gruppoInvioRepository) {
        this.sessioneRepository = sessioneRepository;
        this.comandaRepository = comandaRepository;
        this.gruppoInvioRepository = gruppoInvioRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse calcola() {
        long numeroSessioni = sessioneRepository.count();
        long totaleCoperti = sessioneRepository.sommaCoperti();
        long numeroComande = comandaRepository.count();

        Map<Integer, List<Duration>> tempiPerPortata = gruppoInvioRepository.trovaTempiPreparazione().stream()
                .collect(Collectors.groupingBy(
                        TempoPreparazioneProiezione::getNumeroPortata,
                        Collectors.mapping(
                                p -> Duration.between(p.getInCodaAt(), p.getProntoAt()),
                                Collectors.toList())));

        List<AnalyticsResponse.TempoPreparazionePortata> tempiMediPerPortata = tempiPerPortata.entrySet().stream()
                .map(entry -> new AnalyticsResponse.TempoPreparazionePortata(
                        entry.getKey(),
                        entry.getValue().stream().mapToLong(Duration::toSeconds).average().orElse(0) / 60.0,
                        entry.getValue().size()))
                .sorted(Comparator.comparingInt(AnalyticsResponse.TempoPreparazionePortata::numeroPortata))
                .toList();

        return new AnalyticsResponse(numeroSessioni, totaleCoperti, numeroComande, tempiMediPerPortata);
    }
}
