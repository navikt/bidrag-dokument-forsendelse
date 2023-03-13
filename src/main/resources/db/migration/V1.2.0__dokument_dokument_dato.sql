alter table dokument
    add column if not exists dokument_dato timestamp DEFAULT now() NOT NULL;

update dokument
set dokument_dato = opprettet_tidspunkt::timestamp;