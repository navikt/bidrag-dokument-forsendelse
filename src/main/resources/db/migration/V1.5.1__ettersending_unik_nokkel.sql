ALTER TABLE ettersendingsoppgave
    ADD CONSTRAINT ettersendingsoppgave_unique_forsendelse_id UNIQUE (forsendelse_id);