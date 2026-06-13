# DICOM Modality Simulator

This tool simulates a real modality workflow against a split-server PACS stack:

1. Login to `UDAYA_PACS_API`
2. Create a patient
3. Create a Worklist
4. Send Worklist to PACS
5. Auto-register the simulator AE in DICOM Server through the official REST API
6. Query DICOM Server Modality Worklist over DICOM C-FIND
7. Start scan by moving Worklist to `IN_PROGRESS`
8. Generate DICOM files locally, or rewrite a downloaded real DICOM sample onto the new Worklist
9. Send them to DicomServer over DICOM C-STORE
10. Wait for DicomServer stable-study callback to link study metadata while Worklist remains `IN_PROGRESS`
11. Verify viewer metadata
12. Stop there; the current Worklist model keeps the Worklist `IN_PROGRESS` and stores result state on the study archive

It is intended for full A-to-Z testing when you do not have a real modality machine yet.

## Official References

- `pynetdicom` worklist examples: https://pydicom.github.io/pynetdicom/dev/examples/basic_worklist.html
- `pynetdicom` examples index: https://pydicom.github.io/pynetdicom/stable/examples/
- `pynetdicom` GitHub repo: https://github.com/pydicom/pynetdicom

For convenience, a shallow clone of `pynetdicom` is stored locally at:

- `tools/.cache/vendor/pynetdicom`

## Install

From `D:\Soklin\PACS_System\PACS_API\tools\dicom_modality_simulator`

```powershell
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
```

## Run

```powershell
$env:SIM_API_BASE_URL="http://UDAYA_PACS_API_SERVER_IP:8080/pacsApi"
$env:SIM_DICOM_SERVER_USERNAME="<dicom-server-user>"
$env:SIM_DICOM_SERVER_PASSWORD="<dicom-server-password>"
$env:SIM_USERNAME="<api-user>"
$env:SIM_PASSWORD="<api-password>"
$env:SIM_MODALITY_REGISTRATION_HOST="MODALITY_SIMULATOR_IP"
.\run_local_simulator.ps1
```

Run with a downloaded public DICOM sample as the source image data:

```powershell
$env:SIM_SOURCE_DICOM_URL="https://raw.githubusercontent.com/pydicom/pydicom/main/src/pydicom/data/test_files/CT_small.dcm"
$env:SIM_MODALITY_CODE="CT"
.\run_local_simulator.ps1
```

Required split-server configuration:

- API: `SIM_API_BASE_URL`
- DICOM Server IP/Host, API/Internal Base URL, DICOM port, Viewer Base URL:
  configured dynamically in PACS UI > DICOM Servers and DICOM Routing
- Optional override only for isolated test labs: `SIM_DICOM_REST_BASE_URL`, `SIM_DICOM_HOST`, `SIM_DICOM_PORT`, `SIM_VIEWER_BASE_URL`
- DICOM Server called AE: `SIM_CALLED_AE` or `UDAYA`
- Simulator calling AE: `SIM_CALLING_AE` or `UDAYA`
- DICOM Server REST auth: `SIM_DICOM_SERVER_USERNAME` and `SIM_DICOM_SERVER_PASSWORD`
- API login: `SIM_USERNAME` and `SIM_PASSWORD`
- Simulator modality host: `SIM_MODALITY_REGISTRATION_HOST`
- Optional public or local source DICOM: `SIM_SOURCE_DICOM_URL` or `SIM_SOURCE_DICOM_FILE`

The simulator automatically registers its calling AE as a known DICOM Server modality before issuing the MWL `C-FIND`, because DICOM Server blocks worklist queries from unknown AETs by design.

## Output

Each run writes a folder into:

- `tools/dicom_modality_simulator/runs/<timestamp>/`

Artifacts include:

- generated DICOM files
- `summary.json`

## Notes

- Set `SIM_HOSPITAL_ID`, `SIM_ROUTE_ID`, and `SIM_MODALITY_CODE` when you want to test a specific deployed route, for example an NMCHC X-ray room.
