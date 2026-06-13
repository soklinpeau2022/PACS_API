from __future__ import annotations

import argparse
import json
import os
import sys
import time
import warnings
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import numpy as np
import requests
from pydicom import dcmread
from pydicom.dataset import Dataset, FileDataset, FileMetaDataset
from pydicom.uid import (
    CTImageStorage,
    ExplicitVRLittleEndian,
    MRImageStorage,
    PositronEmissionTomographyImageStorage,
    SecondaryCaptureImageStorage,
    UltrasoundImageStorage,
    generate_uid,
)
from pynetdicom import AE
from pynetdicom.sop_class import ModalityWorklistInformationFind


DEFAULT_OUTPUT_ROOT = Path(__file__).resolve().parent / "runs"
DEFAULT_API_BASE_URL = ""
DEFAULT_VIEWER_BASE_URL = ""
DEFAULT_DICOM_REST_BASE_URL = ""
DEFAULT_DICOM_SERVER_USERNAME = ""
DEFAULT_DICOM_SERVER_PASSWORD = ""
DEFAULT_MODALITY_REGISTRATION_HOST = ""
DEFAULT_MODALITY_REGISTRATION_PORT = 104
DEFAULT_MODALITY_REGISTRATION_KEY = "modalitysim"
DEFAULT_DICOM_HOST = ""
DEFAULT_DICOM_PORT = 4242
DEFAULT_CALLED_AE = "UDAYA"
DEFAULT_CALLING_AE = "UDAYA"
DEFAULT_CLIENT_ID = "pacs-web"
DEFAULT_USERNAME = ""
DEFAULT_PASSWORD = ""
DEFAULT_CALLBACK_WAIT_SECONDS = 45
DEFAULT_STABLE_WAIT_SECONDS = 8
DEFAULT_INSTANCE_COUNT = 3


warnings.filterwarnings(
    "ignore",
    message=r"The value length \(\d+\) exceeds the maximum length of 16 allowed for VR SH\.",
    category=UserWarning,
)


class ApiError(RuntimeError):
    pass


@dataclass
class WorklistFlowContext:
    worklist_id: int
    visit_code: str
    accession_number: str
    patient_uid: str
    patient_name: str
    patient_sex: str
    patient_birth_date: str
    study_description: str
    scheduled_date: str
    scheduled_time: str
    modality_id: int
    modality_code: str
    modality_name: str
    route_hospital_id: int
    route_id: int
    route_dicom_server_id: int
    route_dicom_server_name: str
    route_dicom_server_base_url: str
    route_ae_title: str
    machine_ae_title: str


class EmrApiClient:
    def __init__(
        self,
        base_url: str,
        client_id: str,
        username: str,
        password: str,
        access_token: str | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.client_id = client_id
        self.username = username
        self.password = password
        self.token: str | None = access_token.strip() if access_token else None
        self.session = requests.Session()

    def login(self) -> None:
        response = self._request(
            "POST",
            "/auth/auth-login",
            {
                "clientId": self.client_id,
                "username": self.username,
                "password": self.password,
            },
            authenticated=False,
        )
        rows = response["body"].get("data") or []
        if not rows:
            raise ApiError("Login succeeded but access token was not returned.")
        self.token = rows[0]["accessToken"]

    def discover_best_route(
        self,
        hospital_id: int | None = None,
        hospital_name: str | None = None,
        modality_code: str | None = None,
        route_id: int | None = None,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {"page": 1, "rowsPerPage": 50, "searchText": ""}
        if hospital_id and hospital_id > 0:
            payload["hospitalId"] = hospital_id
        response = self.post("/dicom-routing/dicom-routing-list", payload)
        configs = response["body"].get("data") or []
        if not configs:
            raise ApiError("No DICOM routing configuration found.")

        if hospital_name:
            normalized_hospital_name = hospital_name.strip().lower()
            configs = [
                config
                for config in configs
                if normalized_hospital_name in str(config.get("hospitalName") or "").strip().lower()
            ]
            if not configs:
                raise ApiError(f"No DICOM routing configuration found for hospital '{hospital_name}'.")

        if route_id and route_id > 0:
            for config in configs:
                for route in config.get("routes") or []:
                    if int(route.get("id") or 0) == route_id:
                        return {"config": config, "route": route}
            raise ApiError(f"DICOM route {route_id} was not found in the selected routing configuration.")

        preferred_keywords = preferred_modality_keywords(modality_code)
        best_config: dict[str, Any] | None = None
        best_route: dict[str, Any] | None = None
        for config in configs:
            for route in config.get("routes") or []:
                modality_name = str(route.get("modalityName") or "").strip().lower()
                modality_code_value = str(route.get("modalityCode") or "").strip().lower()
                if any(keyword in modality_name or keyword == modality_code_value for keyword in preferred_keywords):
                    best_config = config
                    best_route = route
                    break
            if best_route:
                break

        if best_route is None:
            best_config = configs[0]
            routes = best_config.get("routes") or []
            if not routes:
                raise ApiError("No route rows were found in the selected DICOM routing config.")
            best_route = routes[0]

        return {"config": best_config, "route": best_route}

    def create_patient(
        self,
        patient_name: str,
        phone_number: str,
        sex: str,
        date_of_birth: str,
        hospital_id: int | None = None,
    ) -> int:
        payload: dict[str, Any] = {
            "patientName": patient_name,
            "gender": sex,
            "phoneNumber": phone_number,
            "dateOfBirth": date_of_birth,
        }
        if hospital_id and hospital_id > 0:
            payload["hospitalId"] = hospital_id
        self.post(
            "/patient/patient-create",
            payload,
        )
        lookup_payload: dict[str, Any] = {"page": 1, "rowsPerPage": 20, "searchText": patient_name}
        if hospital_id and hospital_id > 0:
            lookup_payload["hospitalId"] = hospital_id
        response = self.post(
            "/dropdown/dropdown-patient",
            lookup_payload,
        )
        rows = response["body"].get("data") or []
        if not rows:
            raise ApiError(f"Patient '{patient_name}' was created but cannot be found in dropdown lookup.")
        return int(rows[0]["value"])

    def assign_Worklist(
        self,
        patient_id: int,
        modality_id: int,
        study_description: str,
        scheduled_date: str,
        scheduled_time: str,
        notes: str,
        hospital_id: int | None = None,
    ) -> int:
        payload: dict[str, Any] = {
            "patientId": patient_id,
            "modalityId": modality_id,
            "studyDescription": study_description,
            "scheduledDate": scheduled_date,
            "scheduledTime": scheduled_time,
            "notes": notes,
        }
        if hospital_id and hospital_id > 0:
            payload["hospitalId"] = hospital_id
        response = self.post(
            "/worklist/worklist-assign",
            payload,
        )
        row = (response["body"].get("data") or [None])[0]
        if not row or row.get("worklistId") is None:
            raise ApiError("Worklist assign succeeded but worklistId was not returned.")
        return int(row["worklistId"])

    def send_to_pacs(
        self,
        worklist_id: int,
        hospital_id: int | None = None,
        route_id: int | None = None,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {"worklistId": worklist_id}
        if hospital_id and hospital_id > 0:
            payload["hospitalId"] = hospital_id
        if route_id and route_id > 0:
            payload["routeId"] = route_id
        return self.post("/worklist/worklist-send-to-pacs", payload)

    def find_Worklist(self, worklist_id: int, hospital_id: int | None = None) -> dict[str, Any]:
        payload: dict[str, Any] = {"id": worklist_id}
        if hospital_id and hospital_id > 0:
            payload["hospitalId"] = hospital_id
        response = self.post("/worklist/worklist-find", payload)
        rows = response["body"].get("data") or []
        if not rows:
            raise ApiError(f"Worklist {worklist_id} was not found.")
        return rows[0]

    def view_study(self, worklist_id: int, hospital_id: int | None = None) -> dict[str, Any]:
        payload: dict[str, Any] = {"worklistId": worklist_id}
        if hospital_id and hospital_id > 0:
            payload["hospitalId"] = hospital_id
        response = self.post("/worklist/worklist-view-study", payload)
        rows = response["body"].get("data") or []
        if not rows:
            raise ApiError(f"Viewer study metadata not returned for Worklist {worklist_id}.")
        return rows[0]

    def find_study_by_accession(self, accession_number: str, hospital_id: int | None = None) -> dict[str, Any] | None:
        payload: dict[str, Any] = {
            "page": 0,
            "rowsPerPage": 10,
            "accessionNumber": accession_number,
        }
        if hospital_id and hospital_id > 0:
            payload["hospitalId"] = hospital_id
        response = self.post("/study/study-list", payload)
        rows = response["body"].get("data") or []
        for row in rows:
            if str(row.get("accessionNumber") or "") == accession_number:
                return row
        return rows[0] if rows else None

    def post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        return self._request("POST", path, payload, authenticated=True)

    def _request(
        self,
        method: str,
        path: str,
        payload: dict[str, Any] | None,
        *,
        authenticated: bool,
    ) -> dict[str, Any]:
        headers: dict[str, str] = {}
        if authenticated:
            if not self.token:
                raise ApiError("API client is not authenticated.")
            headers["Authorization"] = f"Bearer {self.token}"

        url = f"{self.base_url}{path}"
        response = self.session.request(method, url, json=payload, headers=headers, timeout=30)

        try:
            body = response.json()
        except Exception as error:  # pragma: no cover - defensive
            raise ApiError(f"API returned non-JSON response at {path}: {error}") from error

        if not body.get("success"):
            header = body.get("header") or {}
            body_message = (body.get("body") or {}).get("message")
            message = body_message or header.get("errorText") or f"HTTP {response.status_code}"
            raise ApiError(f"{path} failed: {message}")

        return body


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="DICOM modality simulator for UDAYA_PACS_API + DicomServer.")
    parser.add_argument("--api-base-url", default=os.getenv("SIM_API_BASE_URL", DEFAULT_API_BASE_URL))
    parser.add_argument("--viewer-base-url", default=os.getenv("SIM_VIEWER_BASE_URL", DEFAULT_VIEWER_BASE_URL))
    parser.add_argument(
        "--dicom_server_rest-base-url",
        default=os.getenv("SIM_DICOM_REST_BASE_URL", DEFAULT_DICOM_REST_BASE_URL),
    )
    parser.add_argument("--dicom_server_username", default=os.getenv("SIM_DICOM_SERVER_USERNAME", DEFAULT_DICOM_SERVER_USERNAME))
    parser.add_argument("--dicom_server_password", default=os.getenv("SIM_DICOM_SERVER_PASSWORD", DEFAULT_DICOM_SERVER_PASSWORD))
    parser.add_argument(
        "--modality-registration-host",
        default=os.getenv("SIM_MODALITY_REGISTRATION_HOST", DEFAULT_MODALITY_REGISTRATION_HOST),
    )
    parser.add_argument(
        "--modality-registration-port",
        type=int,
        default=int(os.getenv("SIM_MODALITY_REGISTRATION_PORT", str(DEFAULT_MODALITY_REGISTRATION_PORT))),
    )
    parser.add_argument(
        "--modality-registration-key",
        default=os.getenv("SIM_MODALITY_REGISTRATION_KEY", DEFAULT_MODALITY_REGISTRATION_KEY),
    )
    parser.add_argument("--client-id", default=os.getenv("SIM_CLIENT_ID", DEFAULT_CLIENT_ID))
    parser.add_argument("--username", default=os.getenv("SIM_USERNAME", DEFAULT_USERNAME))
    parser.add_argument("--password", default=os.getenv("SIM_PASSWORD", DEFAULT_PASSWORD))
    parser.add_argument("--access-token", default=os.getenv("SIM_ACCESS_TOKEN", ""))
    parser.add_argument("--dicom-host", default=os.getenv("SIM_DICOM_HOST", DEFAULT_DICOM_HOST))
    parser.add_argument("--dicom-port", type=int, default=int(os.getenv("SIM_DICOM_PORT", "0") or "0"))
    parser.add_argument("--called-ae", default=os.getenv("SIM_CALLED_AE", DEFAULT_CALLED_AE))
    parser.add_argument("--calling-ae", default=os.getenv("SIM_CALLING_AE", DEFAULT_CALLING_AE))
    parser.add_argument("--hospital-id", type=int, default=int(os.getenv("SIM_HOSPITAL_ID", "0") or "0"))
    parser.add_argument("--hospital-name", default=os.getenv("SIM_HOSPITAL_NAME", ""))
    parser.add_argument("--route-id", type=int, default=int(os.getenv("SIM_ROUTE_ID", "0") or "0"))
    parser.add_argument("--modality-code", default=os.getenv("SIM_MODALITY_CODE", "CT"))
    parser.add_argument("--scheduled-date", default=os.getenv("SIM_SCHEDULED_DATE", ""))
    parser.add_argument("--scheduled-time", default=os.getenv("SIM_SCHEDULED_TIME", "10:00"))
    parser.add_argument("--instances", type=int, default=DEFAULT_INSTANCE_COUNT)
    parser.add_argument("--source-dicom-url", default=os.getenv("SIM_SOURCE_DICOM_URL", ""))
    parser.add_argument("--source-dicom-file", default=os.getenv("SIM_SOURCE_DICOM_FILE", ""))
    parser.add_argument("--stable-wait-seconds", type=int, default=DEFAULT_STABLE_WAIT_SECONDS)
    parser.add_argument("--callback-timeout-seconds", type=int, default=DEFAULT_CALLBACK_WAIT_SECONDS)
    parser.add_argument("--output-root", default=str(DEFAULT_OUTPUT_ROOT))
    parser.add_argument("--skip-modality-registration", action="store_true")
    return parser


def preferred_modality_keywords(modality_code: str | None) -> tuple[str, ...]:
    code = (modality_code or "CT").strip().upper()
    mapping = {
        "CT": ("computed tomography", "ct"),
        "MR": ("magnetic resonance", "mr"),
        "PT": ("positron emission tomography", "pt", "pet"),
        "US": ("ultrasound", "us"),
        "OT": ("other", "ot"),
        "XC": ("external camera photography", "xc"),
        "DX": ("diagnostic x-ray", "dx", "x-ray"),
        "DR": ("digital radiography", "dr"),
        "CR": ("computed radiography", "cr"),
        "MG": ("mammography", "mg"),
        "XA": ("x-ray angiography", "xa"),
    }
    return mapping.get(code, (code.lower(),))


def normalize_birth_date(value: str) -> str:
    return value.replace("-", "")


def normalize_date(value: str) -> str:
    return value.replace("-", "")


def normalize_time(value: str) -> str:
    text = value.strip()
    for fmt in ("%H:%M", "%H:%M:%S", "%I:%M %p", "%I:%M:%S %p"):
        try:
            return datetime.strptime(text, fmt).strftime("%H%M%S")
        except ValueError:
            continue
    return text.replace(":", "").replace(" ", "")


def preferred_storage(modality_code: str) -> tuple[str, Any]:
    code = (modality_code or "").strip().upper()
    mapping = {
        "CT": ("CT", CTImageStorage),
        "MR": ("MR", MRImageStorage),
        "PT": ("PT", PositronEmissionTomographyImageStorage),
        "US": ("US", UltrasoundImageStorage),
        "OT": ("OT", SecondaryCaptureImageStorage),
        "XC": ("XC", SecondaryCaptureImageStorage),
        "DX": ("DX", SecondaryCaptureImageStorage),
    }
    return mapping.get(code, ("OT", SecondaryCaptureImageStorage))


def download_source_dicom(source_url: str, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    target = output_dir / Path(source_url.split("?", 1)[0]).name
    if not target.name.lower().endswith(".dcm"):
        target = output_dir / "source.dcm"

    response = requests.get(source_url, timeout=60)
    response.raise_for_status()
    target.write_bytes(response.content)
    dcmread(str(target), stop_before_pixels=True, force=True)
    return target


def prepare_source_dicom_files(args: argparse.Namespace, run_dir: Path) -> list[Path]:
    source_files: list[Path] = []
    if args.source_dicom_file.strip():
        source_files.append(Path(args.source_dicom_file).resolve())
    if args.source_dicom_url.strip():
        source_files.append(download_source_dicom(args.source_dicom_url.strip(), run_dir / "downloaded-source"))

    for source_file in source_files:
        if not source_file.exists():
            raise FileNotFoundError(f"Source DICOM file was not found: {source_file}")
        dcmread(str(source_file), stop_before_pixels=True, force=True)

    return source_files


def register_modality_with_dicom_server(
    dicom_server_rest_base_url: str,
    dicom_server_username: str,
    dicom_server_password: str,
    modality_key: str,
    calling_ae: str,
    modality_host: str,
    modality_port: int,
) -> dict[str, Any]:
    if not dicom_server_rest_base_url:
        raise RuntimeError("DicomServer REST base URL is required to register the simulator modality.")

    payload = {
        "AET": calling_ae,
        "Host": modality_host,
        "Port": modality_port,
        "AllowEcho": True,
        "AllowFind": True,
        "AllowFindWorklist": True,
        "AllowStore": True,
        "AllowMove": False,
        "Manufacturer": "Generic",
    }

    response = requests.put(
        f"{dicom_server_rest_base_url.rstrip('/')}/modalities/{modality_key}",
        json=payload,
        auth=(dicom_server_username, dicom_server_password),
        timeout=30,
    )
    if response.status_code >= 400:
        raise RuntimeError(
            f"Unable to register simulator modality in DicomServer: HTTP {response.status_code} {response.text}"
        )

    return payload


def query_worklist(
    accession_number: str,
    dicom_host: str,
    dicom_port: int,
    called_ae: str,
    calling_ae: str,
    station_ae_title: str,
) -> list[dict[str, Any]]:
    ae = AE(ae_title=calling_ae)
    ae.add_requested_context(ModalityWorklistInformationFind)
    assoc = ae.associate(dicom_host, dicom_port, ae_title=called_ae)
    if not assoc.is_established:
        raise RuntimeError(f"Unable to associate to DICOM worklist at {dicom_host}:{dicom_port} AE={called_ae}.")

    query = Dataset()
    query.AccessionNumber = accession_number
    query.PatientName = ""
    query.PatientID = ""
    query.StudyDescription = ""
    step = Dataset()
    step.ScheduledStationAETitle = station_ae_title
    step.Modality = ""
    query.ScheduledProcedureStepSequence = [step]

    responses: list[dict[str, Any]] = []
    try:
        for status, identifier in assoc.send_c_find(query, ModalityWorklistInformationFind):
            if status and getattr(status, "Status", None) in (0xFF00, 0xFF01) and identifier is not None:
                responses.append(dataset_to_dict(identifier))
    finally:
        assoc.release()

    return responses


def dataset_to_dict(ds: Dataset) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for elem in ds:
        if elem.VR == "SQ":
            result[elem.keyword or str(elem.tag)] = [dataset_to_dict(item) for item in elem.value]
        else:
            value = elem.value
            if isinstance(value, bytes):
                result[elem.keyword or str(elem.tag)] = f"<bytes:{len(value)}>"
            else:
                result[elem.keyword or str(elem.tag)] = str(value)
    return result


def generate_dicom_study(context: WorklistFlowContext, output_dir: Path, instance_count: int) -> list[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    study_uid = generate_uid()
    series_uid = generate_uid()
    modality_code, sop_class = preferred_storage(context.modality_code)
    now = datetime.now()
    paths: list[Path] = []

    for index in range(instance_count):
        file_meta = FileMetaDataset()
        file_meta.FileMetaInformationVersion = b"\x00\x01"
        file_meta.MediaStorageSOPClassUID = sop_class
        file_meta.MediaStorageSOPInstanceUID = generate_uid()
        file_meta.TransferSyntaxUID = ExplicitVRLittleEndian
        file_meta.ImplementationClassUID = generate_uid()

        path = output_dir / f"{context.accession_number}-{index + 1:04d}.dcm"
        ds = FileDataset(str(path), {}, file_meta=file_meta, preamble=b"\0" * 128)
        ds.is_little_endian = True
        ds.is_implicit_VR = False

        ds.SpecificCharacterSet = "ISO_IR 100"
        ds.SOPClassUID = sop_class
        ds.SOPInstanceUID = file_meta.MediaStorageSOPInstanceUID
        ds.StudyInstanceUID = study_uid
        ds.SeriesInstanceUID = series_uid
        ds.Modality = modality_code
        ds.PatientID = context.patient_uid
        ds.PatientName = context.patient_name
        ds.PatientSex = context.patient_sex or "O"
        ds.PatientBirthDate = normalize_birth_date(context.patient_birth_date)
        ds.AccessionNumber = context.accession_number
        ds.StudyDescription = context.study_description
        ds.RequestedProcedureDescription = context.study_description
        ds.RequestedProcedureID = context.accession_number
        ds.StudyID = context.accession_number
        ds.SeriesDescription = f"{context.study_description} Series"
        ds.StudyDate = normalize_date(context.scheduled_date)
        ds.StudyTime = normalize_time(context.scheduled_time)
        ds.SeriesDate = ds.StudyDate
        ds.SeriesTime = normalize_time((now + timedelta(seconds=index)).strftime("%H:%M:%S"))
        ds.AcquisitionDate = ds.StudyDate
        ds.AcquisitionTime = ds.SeriesTime
        ds.ContentDate = ds.StudyDate
        ds.ContentTime = ds.SeriesTime
        ds.InstanceCreationDate = ds.StudyDate
        ds.InstanceCreationTime = ds.SeriesTime
        ds.SeriesNumber = 1
        ds.InstanceNumber = index + 1
        ds.ImageType = ["ORIGINAL", "PRIMARY", "AXIAL"]
        ds.SamplesPerPixel = 1
        ds.PhotometricInterpretation = "MONOCHROME2"
        ds.Rows = 128
        ds.Columns = 128
        ds.BitsAllocated = 16
        ds.BitsStored = 16
        ds.HighBit = 15
        ds.PixelRepresentation = 0
        ds.PixelSpacing = [1.0, 1.0]
        ds.SliceThickness = 1.0
        ds.SpacingBetweenSlices = 1.0
        ds.ImageOrientationPatient = [1, 0, 0, 0, 1, 0]
        ds.ImagePositionPatient = [0, 0, float(index)]
        ds.RescaleIntercept = 0
        ds.RescaleSlope = 1
        ds.WindowCenter = 128
        ds.WindowWidth = 256
        ds.KVP = 120
        ds.PatientPosition = "HFS"
        ds.Manufacturer = "UDAYA Simulator"
        ds.InstitutionName = "UDAYA_DICOM_SERVER"
        ds.StationName = context.machine_ae_title or context.route_ae_title or "UDAYA"

        gradient = np.tile(np.linspace(0, 1024, ds.Columns, dtype=np.uint16), (ds.Rows, 1))
        image = np.roll(gradient, shift=index * 7, axis=1)
        ds.PixelData = image.tobytes()

        ds.save_as(path, write_like_original=False)
        paths.append(path)

    return paths


def generate_dicom_study_from_source(
    context: WorklistFlowContext,
    source_files: list[Path],
    output_dir: Path,
    instance_count: int,
) -> list[Path]:
    if not source_files:
        return generate_dicom_study(context, output_dir, instance_count)

    output_dir.mkdir(parents=True, exist_ok=True)
    study_uid = generate_uid()
    series_uid = generate_uid()
    modality_code, sop_class = preferred_storage(context.modality_code)
    now = datetime.now()
    paths: list[Path] = []

    for index in range(instance_count):
        source_path = source_files[index % len(source_files)]
        ds = dcmread(str(source_path), force=True)
        ds.remove_private_tags()

        if not getattr(ds, "file_meta", None):
            ds.file_meta = FileMetaDataset()

        sop_instance_uid = generate_uid()
        ds.SpecificCharacterSet = getattr(ds, "SpecificCharacterSet", "ISO_IR 100")
        ds.SOPClassUID = sop_class
        ds.SOPInstanceUID = sop_instance_uid
        ds.StudyInstanceUID = study_uid
        ds.SeriesInstanceUID = series_uid
        ds.Modality = modality_code
        ds.PatientID = context.patient_uid
        ds.PatientName = context.patient_name
        ds.PatientSex = context.patient_sex or "O"
        ds.PatientBirthDate = normalize_birth_date(context.patient_birth_date)
        ds.AccessionNumber = context.accession_number
        ds.StudyDescription = context.study_description
        ds.RequestedProcedureDescription = context.study_description
        ds.RequestedProcedureID = context.accession_number
        ds.StudyID = context.accession_number
        ds.SeriesDescription = f"{context.study_description} Real DICOM Series"
        ds.StudyDate = normalize_date(context.scheduled_date)
        ds.StudyTime = normalize_time(context.scheduled_time)
        ds.SeriesDate = ds.StudyDate
        ds.SeriesTime = normalize_time((now + timedelta(seconds=index)).strftime("%H:%M:%S"))
        ds.AcquisitionDate = ds.StudyDate
        ds.AcquisitionTime = ds.SeriesTime
        ds.ContentDate = ds.StudyDate
        ds.ContentTime = ds.SeriesTime
        ds.InstanceCreationDate = ds.StudyDate
        ds.InstanceCreationTime = ds.SeriesTime
        ds.SeriesNumber = 1
        ds.InstanceNumber = index + 1
        ds.PatientPosition = getattr(ds, "PatientPosition", "HFS")
        ds.InstitutionName = "UDAYA_DICOM_SERVER"
        ds.StationName = context.machine_ae_title or context.route_ae_title or "UDAYA"

        ds.file_meta.FileMetaInformationVersion = b"\x00\x01"
        ds.file_meta.MediaStorageSOPClassUID = sop_class
        ds.file_meta.MediaStorageSOPInstanceUID = sop_instance_uid
        if "TransferSyntaxUID" not in ds.file_meta:
            ds.file_meta.TransferSyntaxUID = ExplicitVRLittleEndian
        if "ImplementationClassUID" not in ds.file_meta:
            ds.file_meta.ImplementationClassUID = generate_uid()

        path = output_dir / f"{context.accession_number}-real-{index + 1:04d}.dcm"
        ds.save_as(path, write_like_original=False)
        paths.append(path)

    return paths


def send_study(
    dicom_files: list[Path],
    modality_code: str,
    dicom_host: str,
    dicom_port: int,
    called_ae: str,
    calling_ae: str,
) -> list[dict[str, Any]]:
    _, sop_class = preferred_storage(modality_code)
    ae = AE(ae_title=calling_ae)
    ae.add_requested_context(sop_class)
    assoc = ae.associate(dicom_host, dicom_port, ae_title=called_ae)
    if not assoc.is_established:
        raise RuntimeError(f"Unable to associate for C-STORE to {dicom_host}:{dicom_port} AE={called_ae}.")

    results: list[dict[str, Any]] = []
    try:
        for dicom_file in dicom_files:
            payload = dcmread(str(dicom_file))
            status = assoc.send_c_store(payload)
            code = getattr(status, "Status", None) if status is not None else None
            results.append(
                {
                    "file": str(dicom_file),
                    "status": f"0x{code:04X}" if isinstance(code, int) else None,
                    "success": isinstance(code, int) and code == 0x0000,
                }
            )
    finally:
        assoc.release()

    return results


def wait_for_image_received(
    api: EmrApiClient,
    worklist_id: int,
    timeout_seconds: int,
    hospital_id: int | None = None,
) -> dict[str, Any]:
    deadline = time.time() + timeout_seconds
    latest: dict[str, Any] | None = None
    while time.time() < deadline:
        latest = api.find_Worklist(worklist_id, hospital_id)
        status = str(latest.get("status") or "").strip().upper()
        has_study = bool(str(latest.get("dicomServerStudyId") or "").strip()) or bool(
            str(latest.get("studyInstanceUid") or "").strip()
        )
        if status == "IN_PROGRESS" and has_study:
            return latest
        time.sleep(2)
    raise ApiError(
        f"Worklist {worklist_id} did not receive study metadata within {timeout_seconds} seconds."
        + (f" Last status={latest.get('status')}" if latest else "")
    )


def first_present(*values: Any, default: Any = "") -> Any:
    for value in values:
        if isinstance(value, list):
            for item in value:
                if str(item or "").strip():
                    return item
        elif str(value or "").strip():
            return value
    return default


def build_context(
    Worklist_row: dict[str, Any],
    route_config: dict[str, Any],
    route_row: dict[str, Any],
) -> WorklistFlowContext:
    dicom_server_id = first_present(
        route_row.get("dicomServerId"),
        route_config.get("dicomServerId"),
        route_config.get("dicomServerIds"),
        default=0,
    )
    dicom_server_base_url = first_present(
        route_row.get("dicomServerBaseUrl"),
        route_row.get("baseUrl"),
        route_config.get("dicomServerBaseUrl"),
        route_config.get("dicomServerBaseUrls"),
    )
    return WorklistFlowContext(
        worklist_id=int(Worklist_row["id"]),
        visit_code=str(Worklist_row["visitCode"]),
        accession_number=str(Worklist_row["accessionNumber"]),
        patient_uid=str(Worklist_row["patientUid"]),
        patient_name=str(Worklist_row["patientName"]),
        patient_sex=str(Worklist_row.get("sex") or "O"),
        patient_birth_date=str(Worklist_row.get("dob") or Worklist_row.get("patientBirthDate") or "1999-01-01"),
        study_description=str(Worklist_row["studyDescription"]),
        scheduled_date=str(Worklist_row["scheduledDate"]),
        scheduled_time=str(Worklist_row["scheduledTime"]),
        modality_id=int(route_row["modalityId"]),
        modality_code=str(Worklist_row.get("modalityCode") or route_row.get("modalityCode") or "CT"),
        modality_name=str(route_row["modalityName"]),
        route_hospital_id=int(route_config["hospitalId"]),
        route_id=int(route_row["id"]),
        route_dicom_server_id=int(dicom_server_id),
        route_dicom_server_name=str(first_present(route_row.get("dicomServerName"), route_config.get("dicomServerName"), route_config.get("dicomServerNames"))),
        route_dicom_server_base_url=str(dicom_server_base_url),
        route_ae_title=str(route_row.get("aeTitle") or DEFAULT_CALLED_AE),
        machine_ae_title=str(
            Worklist_row.get("machineAeTitle")
            or route_row.get("machineAeTitle")
            or route_row.get("aeTitle")
            or DEFAULT_CALLED_AE
        ),
    )


def ensure_viewer_ready(viewer_study: dict[str, Any], viewer_base_url: str) -> None:
    if not str(viewer_base_url or "").strip():
        return
    reported_base = str(viewer_study.get("viewerBaseUrl") or "").rstrip("/")
    expected_base = viewer_base_url.rstrip("/")
    if reported_base and reported_base != expected_base:
        raise ApiError(f"Viewer base URL mismatch. Expected {expected_base}, API returned {reported_base}.")


def run_flow(args: argparse.Namespace) -> dict[str, Any]:
    api = EmrApiClient(args.api_base_url, args.client_id, args.username, args.password, args.access_token)
    if not api.token:
        api.login()

    discovered = api.discover_best_route(
        hospital_id=args.hospital_id if args.hospital_id > 0 else None,
        hospital_name=args.hospital_name or None,
        modality_code=args.modality_code,
        route_id=args.route_id if args.route_id > 0 else None,
    )
    route_config = discovered["config"]
    route_row = discovered["route"]
    modality_id = int(route_row["modalityId"])

    run_stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    patient_name = f"Simulator Patient {run_stamp}"
    phone_number = f"097{datetime.now().strftime('%H%M%S')}"
    scheduled_date = args.scheduled_date.strip() or datetime.now().strftime("%Y-%m-%d")
    scheduled_time = args.scheduled_time.strip() or "10:00"
    target_hospital_id = int(route_config["hospitalId"])
    patient_id = api.create_patient(patient_name, phone_number, "M", "1999-01-01", target_hospital_id)
    worklist_id = api.assign_Worklist(
        patient_id,
        modality_id,
        f"Simulator Study {run_stamp}",
        scheduled_date,
        scheduled_time,
        "Generated by local modality simulator",
        target_hospital_id,
    )

    send_response = api.send_to_pacs(worklist_id, target_hospital_id, int(route_row["id"]))
    send_row = (send_response["body"].get("data") or [None])[0]
    Worklist_before = api.find_Worklist(worklist_id, target_hospital_id)
    context = build_context(Worklist_before, route_config, route_row)
    dicom_host = str(args.dicom_host or route_row.get("ipAddress") or "").strip()
    dicom_port = int(args.dicom_port or route_row.get("dicomPort") or DEFAULT_DICOM_PORT)
    if not dicom_host:
        raise ApiError("DICOM host is missing. Configure the DICOM Server IP/Host in PACS UI > DICOM Servers.")
    dicom_server_rest_base_url = str(args.dicom_server_rest_base_url or "").strip() or str(
        context.route_dicom_server_base_url or ""
    ).strip()
    if not dicom_server_rest_base_url:
        raise ApiError("DicomServer REST base URL is missing. Configure API/Internal Base URL in PACS UI > DICOM Servers.")
    if args.skip_modality_registration:
        registered_modality = {
            "AET": args.calling_ae,
            "Host": args.modality_registration_host,
            "Port": args.modality_registration_port,
            "Skipped": True,
        }
    else:
        registered_modality = register_modality_with_dicom_server(
            dicom_server_rest_base_url,
            args.dicom_server_username,
            args.dicom_server_password,
            args.modality_registration_key,
            args.calling_ae,
            args.modality_registration_host,
            args.modality_registration_port,
        )

    worklist_matches = query_worklist(
        context.accession_number,
        dicom_host,
        dicom_port,
        context.route_ae_title or args.called_ae,
        args.calling_ae,
        context.machine_ae_title or context.route_ae_title or args.called_ae,
    )

    if str(Worklist_before.get("status") or "").strip().upper() == "IN_PROGRESS":
        in_progress_row = Worklist_before
    else:
        in_progress_response = api.send_to_pacs(worklist_id, target_hospital_id, int(route_row["id"]))
        in_progress_row = (in_progress_response["body"].get("data") or [None])[0]

    output_root = Path(args.output_root).resolve()
    run_dir = output_root / run_stamp
    generated_dir = run_dir / "dicom"
    source_files = prepare_source_dicom_files(args, run_dir)
    dicom_files = generate_dicom_study_from_source(context, source_files, generated_dir, args.instances)
    c_store_results = send_study(
        dicom_files,
        context.modality_code,
        dicom_host,
        dicom_port,
        context.route_ae_title or args.called_ae,
        args.calling_ae,
    )

    time.sleep(max(args.stable_wait_seconds, 1))
    image_received = wait_for_image_received(api, worklist_id, args.callback_timeout_seconds, target_hospital_id)
    viewer_study = api.view_study(worklist_id, target_hospital_id)
    study_row = api.find_study_by_accession(context.accession_number, target_hospital_id)
    ensure_viewer_ready(viewer_study, args.viewer_base_url)

    final_Worklist = image_received

    summary = {
        "runTimestamp": run_stamp,
        "apiBaseUrl": args.api_base_url,
        "viewerBaseUrl": args.viewer_base_url or viewer_study.get("viewerBaseUrl"),
        "dicomHost": dicom_host,
        "dicomPort": dicom_port,
        "calledAeTitle": context.route_ae_title or args.called_ae,
        "callingAeTitle": args.calling_ae,
        "patientId": patient_id,
        "patientName": patient_name,
        "worklistId": worklist_id,
        "visitCode": context.visit_code,
        "accessionNumber": context.accession_number,
        "modality": {
            "id": context.modality_id,
            "code": context.modality_code,
            "name": context.modality_name,
        },
        "route": {
            "hospitalId": context.route_hospital_id,
            "routeId": context.route_id,
            "dicomServerId": context.route_dicom_server_id,
            "dicomServerName": context.route_dicom_server_name,
            "dicomServerBaseUrl": context.route_dicom_server_base_url,
            "aeTitle": context.route_ae_title,
            "machineAeTitle": context.machine_ae_title,
        },
        "modalityRegistration": {
            "dicomServerRestBaseUrl": dicom_server_rest_base_url,
            "key": args.modality_registration_key,
            "callingAeTitle": args.calling_ae,
            "host": registered_modality["Host"],
            "port": registered_modality["Port"],
        },
        "WorklistFlow": {
            "statusAfterSendToPacs": send_row["status"] if send_row else None,
            "statusAfterStartScan": in_progress_row["status"] if in_progress_row else None,
            "statusAfterCallback": image_received.get("status"),
            "finalStatus": final_Worklist.get("status"),
        },
        "dicomServer": {
            "worklistMatches": worklist_matches,
            "dicomServerStudyId": image_received.get("dicomServerStudyId"),
            "dicomServerPatientId": image_received.get("dicomServerPatientId"),
            "dicomServerSeriesId": image_received.get("dicomServerSeriesId"),
            "imageReceivedAt": image_received.get("imageReceivedAt"),
        },
        "viewer": {
            "viewerUrl": viewer_study.get("viewerUrl"),
            "WorklistStatus": viewer_study.get("status"),
            "studyInstanceUid": viewer_study.get("studyInstanceUid"),
            "previewCount": len(viewer_study.get("instances") or []),
        },
        "dicomSend": {
            "generatedDirectory": str(generated_dir),
            "sourceDicomUrl": args.source_dicom_url,
            "sourceDicomFiles": [str(path) for path in source_files],
            "files": [str(path) for path in dicom_files],
            "results": c_store_results,
        },
        "studyFlow": {
            "studyStatus": study_row.get("status") if study_row else None,
            "studyId": study_row.get("id") if study_row else None,
            "finalWorklistStatus": final_Worklist.get("status"),
        },
    }

    run_dir.mkdir(parents=True, exist_ok=True)
    (run_dir / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return summary


def main() -> int:
    parser = build_arg_parser()
    args = parser.parse_args()
    required_values = (
        ("SIM_API_BASE_URL / --api-base-url", args.api_base_url),
        ("SIM_DICOM_SERVER_USERNAME / --dicom_server_username", args.dicom_server_username),
        ("SIM_DICOM_SERVER_PASSWORD / --dicom_server_password", args.dicom_server_password),
        ("SIM_MODALITY_REGISTRATION_HOST / --modality-registration-host", args.modality_registration_host),
        ("SIM_CALLED_AE / --called-ae", args.called_ae),
        ("SIM_CALLING_AE / --calling-ae", args.calling_ae),
    )
    if not str(args.access_token or "").strip():
        required_values = (
            *required_values,
            ("SIM_USERNAME / --username", args.username),
            ("SIM_PASSWORD / --password", args.password),
        )
    missing = [name for name, value in required_values if not str(value or "").strip()]
    if missing:
        print("SIMULATOR FAILED: missing required config: " + ", ".join(missing), file=sys.stderr)
        return 1
    try:
        summary = run_flow(args)
    except Exception as error:
        print(f"SIMULATOR FAILED: {error}", file=sys.stderr)
        return 1

    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
