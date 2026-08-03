package com.alnumerocinque.web.dto;

import java.util.List;

public record AnalyticsResponse(
        long numeroSessioni,
        long totaleCoperti,
        long numeroComande,
        List<TempoPreparazionePortata> tempiMediPreparazionePerPortata
) {
    public record TempoPreparazionePortata(
            int numeroPortata,
            double minutiMediPreparazione,
            long campioni
    ) {
    }
}
