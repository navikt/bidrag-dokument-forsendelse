drop rule if exists beskytt_rad_som_ikke_har_dokumentreferanse_original on dokument;
create rule beskytt_rad_som_ikke_har_ekstern_referanse as
    on delete to dokument
    where dokumentreferanse_original is null and journalpost_id_original is null
    do instead nothing;