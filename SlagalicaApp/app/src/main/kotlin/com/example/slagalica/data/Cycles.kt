package com.example.slagalica.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/**
 * Identifikatori nedeljnog i mjesečnog ciklusa, izvedeni IZ datuma (čista
 * funkcija, bez čuvanja "kad ističe"). Osnova lazy reconcile-a: kad se ID
 * razlikuje od zadnje viđenog, ciklus se promijenio → reset zvezda.
 *
 * Primjeri: mjesečni "2026-07", nedeljni "2026-W27" (ISO sedmica).
 */
object Cycles {

    private val zona: ZoneId = ZoneId.systemDefault()

    fun danas(): LocalDate = LocalDate.now(zona)

    fun monthly(date: LocalDate = danas()): String =
        "%04d-%02d".format(date.year, date.monthValue)

    fun weekly(date: LocalDate = danas()): String {
        val wf = WeekFields.ISO
        val sedmica = date.get(wf.weekOfWeekBasedYear())
        val godina = date.get(wf.weekBasedYear())
        return "%04d-W%02d".format(godina, sedmica)
    }

    /** Broj punih dana između timestamp-a (ms) i danas; 0 ako je isti dan ili u budućnosti. */
    fun danaOd(timestampMs: Long): Long {
        if (timestampMs <= 0L) return 0L
        val od = Instant.ofEpochMilli(timestampMs).atZone(zona).toLocalDate()
        return ChronoUnit.DAYS.between(od, danas()).coerceAtLeast(0L)
    }
}
