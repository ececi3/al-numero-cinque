package com.alnumerocinque.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Registra un tavolo aggregato a una sessione gia' aperta (es. due tavoli
 * accostati per un gruppo numeroso). Operazione online-only: non cambia il
 * tavolo "primario" della sessione (vedi nota di design in
 * V1__core_schema.sql), si limita ad associare un tavolo aggiuntivo, che
 * viene occupato come il primario e liberato alla chiusura della sessione
 * (vedi SessioneService).
 */
@Entity
@Table(name = "tavolo_aggregato")
@Getter
@NoArgsConstructor
public class TavoloAggregato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sessione_id", nullable = false)
    private Sessione sessione;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tavolo_id", nullable = false)
    private Tavolo tavolo;

    @Column(name = "aggregato_at", nullable = false)
    private OffsetDateTime aggregatoAt;

    public TavoloAggregato(Sessione sessione, Tavolo tavolo) {
        this.sessione = sessione;
        this.tavolo = tavolo;
        this.aggregatoAt = OffsetDateTime.now();
    }
}
