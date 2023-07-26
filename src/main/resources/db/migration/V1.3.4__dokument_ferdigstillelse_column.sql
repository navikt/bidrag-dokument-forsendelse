alter table dokument
    add column if not exists ferdigstilt_tidspunkt timestamp;

alter table dokument
    add column if not exists ferdigstilt_av_ident text;

CREATE INDEX dokument_metadata_bestilt_antall_ganger ON dokument USING BTREE ((metadata -> 'dokument_bestilt_antall_ganger'));
CREATE INDEX dokument_metadata_encryption_key_version ON dokument USING BTREE ((metadata -> 'gcp_clientside_encryption_key_version'));
