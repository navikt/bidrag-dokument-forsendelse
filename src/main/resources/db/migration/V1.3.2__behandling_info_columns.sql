alter table behandling_info
    add column if not exists engangs_belop_type text;
alter table behandling_info
    add column if not exists stonad_type text;
alter table behandling_info
    add column if not exists vedtak_type text;
alter table behandling_info
    add column if not exists soknad_fra text;
alter table behandling_info
    add column if not exists behandling_type text;
alter table behandling_info
    add column if not exists er_fattet_beregnet boolean;
alter table behandling_info
    add column if not exists er_vedtak_ikke_tilbakekreving boolean;
-- Legacy data fra Bisys for opprettelse av brev via Bisys
alter table behandling_info
    add column if not exists soknad_id text;

CREATE INDEX if not exists idx_beh_info_soknad_id ON behandling_info (soknad_id);
CREATE INDEX if not exists idx_beh_info_vedtak_id ON behandling_info (vedtak_id);
CREATE INDEX if not exists idx_beh_info_behandling_id ON behandling_info (behandling_id);