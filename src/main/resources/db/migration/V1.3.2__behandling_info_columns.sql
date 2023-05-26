alter table behandling_info
    add column if not exists engangs_belop_type text;
alter table behandling_info
    add column if not exists stonad_type text;
alter table behandling_info
    add column if not exists vedtak_type text;
alter table behandling_info
    add column if not exists soknad_fra text;
alter table behandling_info
    add column if not exists vedtak_kilde text;