alter table dokument
    add column if not exists ferdigstilt_tidspunkt timestamp;

alter table dokument
    add column if not exists ferdigstilt_av_ident text;