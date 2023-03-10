CREATE TABLE IF NOT EXISTS adresse
(
    id                  int PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY (INCREMENT 1 START 1 MINVALUE 1),
    adresselinje1       text,
    adresselinje2       text,
    adresselinje3       text,
    bruksenhetsnummer   text,
    landkode            text,
    landkode3           text,
    postnummer          text,
    poststed            text,
    opprettet_tidspunkt timestamp without time zone DEFAULT now() NOT NULL
);