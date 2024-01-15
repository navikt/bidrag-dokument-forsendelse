select * from forsendelse f inner join public.dokument d on f.forsendelse_id = d.forsendelse_id where f.status = 'UNDER_PRODUKSJON' and d.slettet_tidspunkt is null and d.dokument_status = 'UNDER_REDIGERING' and d.arkivsystem != 'FORSENDELSE' and date_trunc('day', d.opprettet_tidspunkt) <= '2023-11-27';

--- Sjekk for forsendelser som har kanal NAV_NO og ikke er sjekket fra før
select f.metadata -> 'sjekket_navno_redistribusjon_til_sentral_print', f.distribuert_tidspunkt from forsendelse f where f.status = 'DISTRIBUERT' and f.distribusjon_kanal = 'NAV_NO' and f.distribuert_tidspunkt <= '2023-12-29 08:55:07.547114' and f.distribuert_tidspunkt >= '2023-01-01 08:55:07.547114' and (f.metadata -> 'sjekket_navno_redistribusjon_til_sentral_print' = 'false' or f.metadata -> 'sjekket_navno_redistribusjon_til_sentral_print' is null) order by f.distribuert_tidspunkt desc
select f.forsendelse_id, f.tittel, f.metadata -> 'sjekket_navno_redistribusjon_til_sentral_print', f.distribuert_tidspunkt from forsendelse f where f.status = 'DISTRIBUERT' and f.distribusjon_kanal = 'NAV_NO' and f.distribuert_tidspunkt <= now() - interval '1 DAY' and f.distribuert_tidspunkt >= now() - interval '30 MONTH' and (f.metadata -> 'sjekket_navno_redistribusjon_til_sentral_print' = 'false' or f.metadata -> 'sjekket_navno_redistribusjon_til_sentral_print' is null) order by f.distribuert_tidspunkt desc
select f.forsendelse_id, f.tittel, f.metadata -> 'sjekket_navno_redistribusjon_til_sentral_print', f.bestilt_ny_distribusjon, f.distribuert_tidspunkt from forsendelse f inner join public.dokument d on f.forsendelse_id = d.forsendelse_id where f.metadata -> 'sjekket_navno_redistribusjon_til_sentral_print' = 'true';
select * from dokument d inner join public.forsendelse f on f.forsendelse_id = d.forsendelse_id where f.status = 'UNDER_PRODUKSJON' and d.slettet_tidspunkt is null and d.dokument_status = 'UNDER_REDIGERING'
and d.arkivsystem != 'FORSENDELSE' and d.opprettet_tidspunkt <= NOW() - INTERVAL '1 DAY' and d.opprettet_tidspunkt > now() - interval '30 DAY' order by d.opprettet_tidspunkt;

select f.distribusjon_kanal, f.distribuert_tidspunkt, f.metadata from forsendelse f where f.bestilt_ny_distribusjon = true;
select distribusjon_kanal, distribuert_tidspunkt from forsendelse f where f.bestilt_ny_distribusjon = true;
select count(*) from forsendelse f where f.distribusjon_kanal = 'NAV_NO';

select count(*) from forsendelse f where f.distribusjon_kanal = 'NAV_NO';

select * from forsendelse f inner join dokument d on f.forsendelse_id = d.forsendelse_id where d.dokumentmal_id = 'BI01S70';
-- Velg alle fra batch
select distribusjon_bestillings_id, forsendelse_id from forsendelse f where f.batch_id = 'FB260' and opprettet_av_ident = 'bisys' and opprettet_tidspunkt > '2022-05-08' order by opprettet_tidspunkt desc;
select count(*) from forsendelse f where f.batch_id = 'FB260' and opprettet_av_ident = 'bisys' and opprettet_tidspunkt > '2022-05-08' and distribusjon_kanal is null;

-- Velg alle fra batch ikke distribuert
select * from forsendelse f where f.batch_id = 'FB260' and opprettet_av_ident = 'bisys' and (status != 'FERDIGSTILT' and status != 'DISTRIBUERT' and status != 'DISTRIBUERT_LOKALT') order by opprettet_tidspunkt desc;

--- Velg alle opprettet av saksbehandler ikke distribuert
select f.forsendelse_id, f.enhet, f.saksnummer, d.tittel, f.opprettet_tidspunkt, d.dokument_dato from forsendelse f inner join dokument d on f.forsendelse_id = d.forsendelse_id
where f.batch_id is null and opprettet_av_ident != 'bisys'
  and d.dokument_status = 'FERDIGSTILT'
  and f.forsendelse_type = 'UTGÅENDE'
  and f.opprettet_tidspunkt < current_date
  and f.status = 'UNDER_PRODUKSJON' order by f.enhet desc, f.opprettet_tidspunkt desc;

select * from forsendelse where forsendelse_id = '1000272871';
select * from forsendelse where saksnummer = '2310197' order by opprettet_tidspunkt desc;

------------------------------------STATISTIKK------------------------------------------------------

--- Forsendelser med samhandler som mottaker

select f.forsendelse_id, f.gjelder_ident, m.ident, f.status, a.adresselinje1, d.tittel, f.batch_id
from forsendelse f
         join mottaker m on m.id = f.mottaker_id
         join adresse a on a.id = m.adresse_id
         join dokument d on f.forsendelse_id = d.forsendelse_id
where m.ident like '800%';

--- Statistikk av forsendelser som har blitt sendt via sentral print etter 40 timer
WITH total AS (
    SELECT count (f.distribusjon_kanal) as kanal_count from forsendelse f where f.distribusjon_kanal = 'NAV_NO'
    group by f.distribusjon_kanal
    ),
redistribuert AS (
    SELECT count(*) as kanal_count
    FROM forsendelse
    where distribusjon_kanal = 'SENTRAL_UTSKRIFT' and bestilt_ny_distribusjon = true),
bare_sentral_print AS (
    SELECT count(*) as kanal_count
    FROM forsendelse
    where distribusjon_kanal = 'SENTRAL_UTSKRIFT' and bestilt_ny_distribusjon = false
)
SELECT redistribuert.kanal_count, bare_sentral_print.kanal_count, total.kanal_count as Alle_NAV_NO, floor(redistribuert.kanal_count / (total.kanal_count)::float * 100) as prosent
FROM forsendelse, total, redistribuert, bare_sentral_print
GROUP BY total.kanal_count, redistribuert.kanal_count, bare_sentral_print.kanal_count;

--- Statistikk av distribusjon kanal for batch
WITH total AS (
    SELECT f.distribusjon_kanal, count (f.distribusjon_kanal) as kanal_count from forsendelse f where distribusjon_kanal is not null and batch_id is not null GROUP BY distribusjon_kanal
)
SELECT total.distribusjon_kanal, total.kanal_count, floor(total.kanal_count / (
    SELECT count(*)
    FROM forsendelse

    where distribusjon_kanal is not null and batch_id is not null
)::float * 100) as prosent
FROM forsendelse, total
WHERE forsendelse.distribusjon_kanal = total.distribusjon_kanal
GROUP BY total.distribusjon_kanal, total.kanal_count;

--- Statistikk av distribusjon kanal for spesifikk batch ----
WITH total AS (
    SELECT f.distribusjon_kanal, count (f.distribusjon_kanal) as kanal_count from forsendelse f where distribusjon_bestillings_id is not null and batch_id = 'FB260' and opprettet_tidspunkt > '2022-05-08' GROUP BY distribusjon_kanal
)
SELECT total.distribusjon_kanal, total.kanal_count, floor(total.kanal_count / (
    SELECT count(*)
    FROM forsendelse

    where distribusjon_bestillings_id is not null and batch_id = 'FB260' and opprettet_tidspunkt > '2022-05-08'
)::float * 100) as prosent
FROM forsendelse, total
WHERE forsendelse.distribusjon_kanal = total.distribusjon_kanal
GROUP BY total.distribusjon_kanal, total.kanal_count;

--- Statistikk av distribusjon kanal for alle
WITH total AS (
    SELECT f.distribusjon_kanal, count (f.distribusjon_kanal) as kanal_count from forsendelse f where distribusjon_kanal is not null GROUP BY distribusjon_kanal
)
SELECT total.distribusjon_kanal, total.kanal_count, floor(total.kanal_count / (
    SELECT count(*)
    FROM forsendelse
    where distribusjon_kanal is not null
)::float * 100) as prosent
FROM forsendelse, total
WHERE forsendelse.distribusjon_kanal = total.distribusjon_kanal
GROUP BY total.distribusjon_kanal, total.kanal_count;

--- Statistikk av distribusjon kanal for ikke batch
WITH total AS (
    SELECT f.distribusjon_kanal, count (f.distribusjon_kanal) as kanal_count from forsendelse f where distribusjon_kanal is not null and batch_id is null GROUP BY distribusjon_kanal
)
SELECT total.distribusjon_kanal, total.kanal_count, floor(total.kanal_count / (
    SELECT count(*)
    FROM forsendelse

    where distribusjon_kanal is not null and batch_id is null
)::float * 100) as prosent
FROM forsendelse, total
WHERE forsendelse.distribusjon_kanal = total.distribusjon_kanal
GROUP BY total.distribusjon_kanal, total.kanal_count;

--- Statistikk av distribusjon kanal for farskap
WITH total AS (
    SELECT f.distribusjon_kanal, count (f.distribusjon_kanal) as kanal_count from forsendelse f where distribusjon_kanal is not null and tema = 'FAR' GROUP BY distribusjon_kanal
)
SELECT total.distribusjon_kanal, total.kanal_count, floor(total.kanal_count / (
    SELECT count(*)
    FROM forsendelse

    where distribusjon_kanal is not null and tema = 'FAR'
)::float * 100) as prosent
FROM forsendelse, total
WHERE forsendelse.distribusjon_kanal = total.distribusjon_kanal
GROUP BY total.distribusjon_kanal, total.kanal_count;

--- Statistikk av enhet for farskap
WITH total AS (
    SELECT f.enhet, count (f.enhet) as enhet_count from forsendelse f where distribusjon_kanal is not null and tema = 'FAR' GROUP BY enhet
)
SELECT total.enhet, total.enhet_count, floor(total.enhet_count / (
    SELECT count(*)
    FROM forsendelse

    where distribusjon_kanal is not null and tema = 'FAR'
)::float * 100) as prosent
FROM forsendelse, total
WHERE forsendelse.enhet = total.enhet
GROUP BY total.enhet, total.enhet_count;
