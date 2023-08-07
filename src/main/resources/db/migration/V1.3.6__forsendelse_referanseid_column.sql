alter table forsendelse
    add column if not exists referanse_id text;
alter table forsendelse
    drop constraint if exists referanse_id_unique;
alter table forsendelse
    add constraint referanse_id_unique UNIQUE (referanse_id);
comment on column forsendelse.referanse_id is 'Unik referanseid som kan brukes til sporing av forsendelsen gjennom verdikjeden. Brukes også for duplikatkontroll slik at samme forsendelse ikke opprettes/lenkes til flere ganger i andre systemer (feks fagarkivet)'