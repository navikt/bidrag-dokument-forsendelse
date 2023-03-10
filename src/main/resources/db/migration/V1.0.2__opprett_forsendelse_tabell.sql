CREATE TABLE IF NOT EXISTS forsendelse
(
    forsendelse_id              bigint PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY (INCREMENT 1 START 1000000000 MINVALUE 1000000000),
    forsendelse_type            text                                      not null,
    journalpost_id_fagarkiv     text,
    enhet                       text,
    språk                       text,
    saksnummer                  text,
    status                      text,
    gjelder_ident               text                                      not null,
    opprettet_av_ident          text,
    endret_av_ident             text,
    avbrutt_av_ident            text,
    distribuert_av_ident        text,
    opprettet_av_navn           text,
    distribusjon_bestillings_id text,
    distribuert_tidspunkt       timestamp without time zone,
    avbrutt_tidspunkt           timestamp without time zone,
    ferdigstilt_tidspunkt       timestamp without time zone,
    opprettet_tidspunkt         timestamp without time zone DEFAULT now() NOT NULL,
    endret_tidspunkt            timestamp without time zone DEFAULT now() NOT NULL,
    mottaker_id                 integer,
    CONSTRAINT fk_mottaker_id FOREIGN KEY (mottaker_id)
        REFERENCES mottaker (id) MATCH SIMPLE
);

CREATE INDEX idx_fors_saksnummer ON forsendelse (saksnummer);
CREATE INDEX idx_fors_jpid ON forsendelse (journalpost_id_fagarkiv);
CREATE INDEX idx_fors_status ON forsendelse (status);