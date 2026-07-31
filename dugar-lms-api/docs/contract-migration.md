# Contract Migration (Legacy Excel)

## Upload Endpoint

- Method: `POST`
- URL: `/api/migration/contracts/import`
- Authentication: Required (JWT)
- Content-Type: `multipart/form-data`
- Form field: `file` (Excel `.xlsx`)

## Expected Sheet

- The workbook must include a sheet named `Active`.
- The first row must contain Oracle column headers.
- Columns are read by header name, not by fixed position.

## Duplicate Rule

- Duplicate key: `(contract_type, contract_number)`
- If a contract already exists, that row is skipped.
- When a row is skipped as duplicate, no `contract_details` row is inserted.

## Transaction Behavior

- Each Excel row is processed in its own database transaction.
- If `contracts` insert or `contract_details` insert fails, the row is rolled back.
- Processing continues for remaining rows.

## Contracts Field Mapping

| Oracle Column | PostgreSQL Column |
|---|---|
| CONT_TYPE | contract_type |
| CONT_NO | contract_number |
| CONT_DT | contract_date |
| EFF_DT | first_emi_date |
| PERIOD | tenure_months |
| FIN_AMT | loan_amount |
| FIN_RATE | flat_interest_rate |
| INS_DEPOSIT | insurance_deposit |
| FIN_CHGS | finance_charges |
| BPFC_DAYS | bpfc_days |
| BPFC_RATE | bpfc_rate |
| BPFC_AMT | bpfc_amount |
| TOT_CONT | total_contract_value |
| PPR_PER | prompt_payment_rebate |
| IRR_RATE | irr_rate |
| STATUS | status |
| HIRER_CODE | borrower_code |
| GUAR_CODE | guarantor_code |
| FLD_CODE | area_code |
| REGIS_NO | registration_number |
| EQUP_CODE | vehicle_make |
| EQUP_MODEL | equipment_model |
| CLOSE_DATE | loan_close_date |
| RISKGRADE | category |
| PDCDATE | pdc_last_date |

## Contract Details Field Mapping

| Oracle Column | PostgreSQL Column |
|---|---|
| EXISTING_HP_NO | existing_loan_details |
| OTHER_VEHICLES | linked_account |
| SERVICE_CHGS | processing_charges |
| FIRST_EMI | emi_advance |
| STATE_CODE | state_code |
| SECURITY_OFFERED | additional_collateral |
| ENGINE_NO | engine_number |
| CHASIS_NO | chassis_number |
| REGN_DATE | registration_date |
| SANCTIONED_BY | loan_approved_by |
| PARTY_INSP_BY | borrower_fi_by |
| VEHICLE_INSP_BY | vehicle_inspection_by |
| DOCUMENT_OBTN_BY | documents_obtained_by |
| INTRODUCED_BY | loan_referred_by |
| NO_OF_OWNERSHIP | owner_serial_number |

## Fields Deliberately Set to NULL

- `contracts.vehicle_age`
- `contracts.mode_of_payment`

## Audit Values

- `created_at`: table default (`CURRENT_TIMESTAMP`)
- `created_by`: `LEGACY_MIGRATION`
- `is_active`: `TRUE`

## Sample curl

```bash
curl -X POST "http://localhost:8080/api/migration/contracts/import" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "file=@Contracts-Active.xlsx"
```

## Response Format

```json
{
  "success": true,
  "fileName": "Contracts-Active (2).xlsx",
  "sheetName": "Active",
  "totalRows": 0,
  "inserted": 0,
  "duplicatesSkipped": 0,
  "failed": 0,
  "errors": [
    {
      "excelRow": 10,
      "contractType": "HP",
      "contractNumber": "A12345",
      "reason": "Invalid numeric value for FIN_AMT"
    }
  ]
}
```

## Notes

- Only `.xlsx` files are supported.
- Errors list is limited to the first 100 row failures, while `failed` contains total failed count.
- Oracle columns without target mapping are ignored.
