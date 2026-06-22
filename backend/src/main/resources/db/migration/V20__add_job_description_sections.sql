ALTER TABLE jobs
    ADD COLUMN description_sections JSON NULL AFTER description;
