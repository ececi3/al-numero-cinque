package com.alnumerocinque.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comanda presa dal cameriere. L'id e' client-generated (append offline-safe).
 * {@code seqServer} e' invece assegnato in modo monotono dal server SOLO al
 * momento della registrazione (vedi CoursingService.registraComanda), e
 * determina l'ordine assoluto di arrivo usato dal motore di coursing —
 * indipendente dall'ordine in cui le comande arrivano fisicamente al server
 * quando i dispositivi tornano online in ordine sparso.
 */
@Entity
@Table(name = "comanda")
@Getter
@NoArgsConstructor
public class Comanda {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sessione_id", nullable = false)
    private Sessione sessione;

    @Column(name = "cameriere_id", nullable = false)
    private Long cameriereId;

    @Column(name = "seq_server", unique = true)
    private Long seqServer;

    @Column(name = "creata_at", nullable = false)
    private OffsetDateTime creataAt;

    @Column(name = "registrata_at")
    private OffsetDateTime registrataAt;

    @OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroPortata ASC")
    private List<GruppoInvio> gruppi = new ArrayList<>();

    public Comanda(UUID id, Sessione sessione, Long cameriereId, OffsetDateTime creataAt) {
        this.id = id;
        this.sessione = sessione;
        this.cameriereId = cameriereId;
        this.creataAt = creataAt;
    }

    public GruppoInvio aggiungiGruppo(int numeroPortata) {
        GruppoInvio gruppo = new GruppoInvio(this, numeroPortata);
        this.gruppi.add(gruppo);
        return gruppo;
    }

    /**
     * Assegna il numero di sequenza server, monotono, al momento in cui la
     * comanda viene registrata (sync). Operazione idempotente: se la comanda
     * e' gia' registrata non viene riassegnato un nuovo seq.
     */
    public void registraSuServer(long seqServer) {
        if (this.seqServer != null) {
            return; // gia' registrata: idempotente rispetto a re-invii dopo riconnessione
        }
        this.seqServer = seqServer;
        this.registrataAt = OffsetDateTime.now();
    }

    public boolean isRegistrata() {
        return this.seqServer != null;
    }
}
