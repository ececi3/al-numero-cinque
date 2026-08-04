package com.alnumerocinque.web.dto;

import com.alnumerocinque.domain.Comanda;
import com.alnumerocinque.domain.RigaOrdine;
import com.alnumerocinque.domain.Sessione;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conto della sessione: somma di prezzoCongelato*quantita su tutte le
 * RigaOrdine delle sue comande (i tavoli aggregati non hanno comande
 * proprie, condividono la stessa sessione, quindi sono gia' inclusi senza
 * logica aggiuntiva). Le righe sono aggregate per (menuItem, prezzo
 * congelato): due ordinazioni della stessa voce allo stesso prezzo
 * compaiono come una sola riga con quantita' sommata; se il prezzo di
 * listino e' cambiato durante la sessione, compaiono come righe distinte
 * (corretto: il prezzo congelato di ciascuna e' quello davvero dovuto).
 */
public record ContoResponse(
        UUID sessioneId,
        Long tavoloId,
        int numeroCoperti,
        List<VoceContoResponse> voci,
        BigDecimal totale
) {
    public static ContoResponse of(Sessione sessione, List<Comanda> comande) {
        Map<ChiaveVoce, VoceAccumulo> accumulo = new LinkedHashMap<>();

        comande.stream()
                .flatMap(c -> c.getGruppi().stream())
                .flatMap(g -> g.getRighe().stream())
                .forEach(riga -> accumulo
                        .computeIfAbsent(ChiaveVoce.of(riga), k -> new VoceAccumulo(riga))
                        .aggiungi(riga));

        List<VoceContoResponse> voci = accumulo.values().stream()
                .map(VoceAccumulo::toResponse)
                .toList();

        BigDecimal totale = voci.stream()
                .map(VoceContoResponse::totaleVoce)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ContoResponse(
                sessione.getId(), sessione.getTavolo().getId(), sessione.getNumeroCoperti(), voci, totale);
    }

    public record VoceContoResponse(Long menuItemId, String nome, int quantita, BigDecimal prezzoUnitario, BigDecimal totaleVoce) {
    }

    private record ChiaveVoce(Long menuItemId, BigDecimal prezzoCongelato) {
        static ChiaveVoce of(RigaOrdine riga) {
            return new ChiaveVoce(riga.getMenuItem().getId(), riga.getPrezzoCongelato());
        }
    }

    private static final class VoceAccumulo {
        private final Long menuItemId;
        private final String nome;
        private final BigDecimal prezzoUnitario;
        private int quantita;

        VoceAccumulo(RigaOrdine primaRiga) {
            this.menuItemId = primaRiga.getMenuItem().getId();
            this.nome = primaRiga.getMenuItem().getNome();
            this.prezzoUnitario = primaRiga.getPrezzoCongelato();
        }

        void aggiungi(RigaOrdine riga) {
            this.quantita += riga.getQuantita();
        }

        VoceContoResponse toResponse() {
            return new VoceContoResponse(menuItemId, nome, quantita, prezzoUnitario, prezzoUnitario.multiply(BigDecimal.valueOf(quantita)));
        }
    }
}
