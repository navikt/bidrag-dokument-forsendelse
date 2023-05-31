alter table behandling_info
    add column if not exists engangs_belop_type text;
alter table behandling_info
    add column if not exists stonad_type text;
alter table behandling_info
    add column if not exists vedtak_type text;
alter table behandling_info
    add column if not exists soknad_fra text;
alter table behandling_info
    add column if not exists er_fattet_beregnet boolean;

-- Legacy data fra Bisys for opprettelse av brev via Bisys
alter table behandling_info
    add column if not exists soknad_id boolean;