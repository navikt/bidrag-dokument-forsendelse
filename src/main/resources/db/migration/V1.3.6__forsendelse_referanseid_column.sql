alter table forsendelse
    add column if not exists referanse_id text;

comment on column forsendelse.referanse_id is 'Unik referanseid som kan brukes til sporing av forsendelsen gjennom verdikjeden.'