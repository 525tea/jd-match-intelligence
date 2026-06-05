ALTER TABLE jobs
    ADD FULLTEXT INDEX ft_jobs_search (
        title,
        company_name,
        description,
        role_detail,
        industry,
        location_region,
        location_city
    );
