ALTER TABLE hospital_dicom_machines
    DROP COLUMN IF EXISTS room_name;

UPDATE module_types
SET name = 'DICOM Machines',
    modified = NOW()
WHERE code = 'DICOM_MACHINE'
  AND name != 'DICOM Machines';

UPDATE modules
SET name = 'DICOM Machines',
    modified = NOW()
WHERE code = 'dicom-machine'
  AND name != 'DICOM Machines';

UPDATE module_details
SET name = REPLACE(name, 'DICOM Machine / Rooms', 'DICOM Machine'),
    modified = NOW()
WHERE code LIKE 'dicom.machine.%'
  AND name LIKE '%DICOM Machine / Rooms%';
