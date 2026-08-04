package com.alnumerocinque.web.dto;

/** {@code note} puo' essere nullo o vuoto per cancellare la nota esistente. */
public record AggiornaNoteRequest(String note) {
}
