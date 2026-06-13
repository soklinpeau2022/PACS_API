UPDATE patients
SET date_of_birth = DATE '1900-01-01'
WHERE date_of_birth IS NULL
  AND is_active = 1;
