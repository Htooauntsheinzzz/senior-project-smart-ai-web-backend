-- Coordinates copied from Google Maps carry up to 15 decimal places (e.g. 13.964438098079592,
-- 100.5866727383530). NUMERIC(10,7) rejected them, so every campus coordinate column now stores
-- 3 integer digits + 15 decimals. Existing values are kept exactly; the range CHECKs are unchanged.
ALTER TABLE campus_sites
    ALTER COLUMN center_latitude TYPE NUMERIC(18,15),
    ALTER COLUMN center_longitude TYPE NUMERIC(18,15);
ALTER TABLE campus_nav_nodes
    ALTER COLUMN latitude TYPE NUMERIC(18,15),
    ALTER COLUMN longitude TYPE NUMERIC(18,15);
ALTER TABLE campus_nav_edge_points
    ALTER COLUMN latitude TYPE NUMERIC(18,15),
    ALTER COLUMN longitude TYPE NUMERIC(18,15);
