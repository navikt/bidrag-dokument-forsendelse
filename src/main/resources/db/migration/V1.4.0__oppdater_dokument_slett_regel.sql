drop rule beskytt_rad_som_ikke_har_ekstern_referanse on dokument;
CREATE RULE beskytt_rad_som_ikke_har_ekstern_referanse AS
    ON DELETE TO dokument
    WHERE old.dokumentreferanse_original IS NULL AND old.journalpost_id_original IS NULL AND
          old.metadata -> 'er_statisk_dokument' is null DO INSTEAD NOTHING;