from __future__ import annotations

import argparse
from datetime import datetime
from pathlib import Path

import numpy as np
from pydicom.dataset import Dataset, FileDataset, FileMetaDataset
from pydicom.uid import CTImageStorage, ExplicitVRLittleEndian, generate_uid


DEFAULT_STUDY_UID = "1.2.276.0.7230010.3.1.2.1633957987.1.1781451786.967672"


def create_ct_callback_test_file(output_path: Path) -> Path:
    output_path.parent.mkdir(parents=True, exist_ok=True)

    accession_number = "CT-KSFH-260614-0002"
    study_date = "20260614"
    study_time = "224200"
    now = datetime.now()

    sop_instance_uid = generate_uid()
    series_instance_uid = generate_uid()

    file_meta = FileMetaDataset()
    file_meta.FileMetaInformationVersion = b"\x00\x01"
    file_meta.MediaStorageSOPClassUID = CTImageStorage
    file_meta.MediaStorageSOPInstanceUID = sop_instance_uid
    file_meta.TransferSyntaxUID = ExplicitVRLittleEndian
    file_meta.ImplementationClassUID = generate_uid()

    ds = FileDataset(str(output_path), {}, file_meta=file_meta, preamble=b"\0" * 128)
    ds.is_little_endian = True
    ds.is_implicit_VR = False

    ds.SpecificCharacterSet = "ISO_IR 192"
    ds.SOPClassUID = CTImageStorage
    ds.SOPInstanceUID = sop_instance_uid
    ds.StudyInstanceUID = DEFAULT_STUDY_UID
    ds.SeriesInstanceUID = series_instance_uid
    ds.Modality = "CT"

    ds.PatientName = "Jame Sok"
    ds.PatientID = "26-KSFH-P0000047"
    ds.PatientBirthDate = "20051203"
    ds.PatientSex = "M"

    ds.AccessionNumber = accession_number
    ds.StudyDescription = "Test"
    ds.RequestedProcedureDescription = "Test"
    ds.RequestedProcedureID = accession_number
    ds.StudyID = accession_number
    ds.StudyDate = study_date
    ds.StudyTime = study_time
    ds.SeriesDate = study_date
    ds.SeriesTime = study_time
    ds.AcquisitionDate = study_date
    ds.AcquisitionTime = study_time
    ds.ContentDate = study_date
    ds.ContentTime = now.strftime("%H%M%S")
    ds.InstanceCreationDate = now.strftime("%Y%m%d")
    ds.InstanceCreationTime = now.strftime("%H%M%S")

    step = Dataset()
    step.Modality = "CT"
    step.ScheduledStationAETitle = "KSFH_CT01"
    step.ScheduledProcedureStepStartDate = study_date
    step.ScheduledProcedureStepStartTime = study_time
    step.ScheduledProcedureStepDescription = "Test"
    step.ScheduledProcedureStepID = accession_number
    ds.ScheduledProcedureStepSequence = [step]

    ds.SeriesDescription = "Test Callback CT Series"
    ds.SeriesNumber = 1
    ds.InstanceNumber = 1
    ds.ImageType = ["ORIGINAL", "PRIMARY", "AXIAL"]
    ds.Manufacturer = "UDAYA PACS Test"
    ds.InstitutionName = "KSFH Hospital"
    ds.StationName = "KSFH_CT01"
    ds.ReferringPhysicianName = ""
    ds.PatientPosition = "HFS"

    ds.SamplesPerPixel = 1
    ds.PhotometricInterpretation = "MONOCHROME2"
    ds.Rows = 256
    ds.Columns = 256
    ds.BitsAllocated = 16
    ds.BitsStored = 16
    ds.HighBit = 15
    ds.PixelRepresentation = 0
    ds.PixelSpacing = [1.0, 1.0]
    ds.SliceThickness = 1.0
    ds.SpacingBetweenSlices = 1.0
    ds.ImageOrientationPatient = [1, 0, 0, 0, 1, 0]
    ds.ImagePositionPatient = [0, 0, 0]
    ds.RescaleIntercept = -1024
    ds.RescaleSlope = 1
    ds.WindowCenter = 40
    ds.WindowWidth = 400
    ds.KVP = 120

    x = np.linspace(0, 1800, ds.Columns, dtype=np.uint16)
    y = np.linspace(0, 700, ds.Rows, dtype=np.uint16)[:, None]
    image = (x + y) % 2048
    ds.PixelData = image.astype(np.uint16).tobytes()

    ds.save_as(output_path, write_like_original=False)
    return output_path


def main() -> int:
    parser = argparse.ArgumentParser(description="Create a CT DICOM file for callback testing.")
    parser.add_argument(
        "--output",
        default="PACS_API/tools/generated-dicom/CT-KSFH-260614-0002/CT-KSFH-260614-0002-0001.dcm",
        help="Output DICOM file path.",
    )
    args = parser.parse_args()

    output_path = create_ct_callback_test_file(Path(args.output).resolve())
    print(output_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
