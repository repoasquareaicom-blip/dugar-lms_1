# Oracle to PostgreSQL Contract Field Mapping

## contracts

| Oracle Column | PostgreSQL Column | Target Table |
|---|---|---|
| CONTRACT_ID | contract_id | contracts |
| CONTRACT_TYPE | contract_type | contracts |
| CONTRACT_NUMBER | contract_number | contracts |
| LEGACY_CONTRACT_NUMBER | legacy_contract_number | contracts |
| CONTRACT_DATE | contract_date | contracts |
| LOAN_AMOUNT | loan_amount | contracts |
| FIRST_EMI_DATE | first_emi_date | contracts |
| TENURE_MONTHS | tenure_months | contracts |
| FLAT_INTEREST_RATE | flat_interest_rate | contracts |
| INSURANCE_DEPOSIT | insurance_deposit | contracts |
| FINANCE_CHARGES | finance_charges | contracts |
| BPFC_DAYS | bpfc_days | contracts |
| BPFC_RATE | bpfc_rate | contracts |
| BPFC_AMOUNT | bpfc_amount | contracts |
| TOTAL_CONTRACT_VALUE | total_contract_value | contracts |
| PROMPT_PAYMENT_REBATE | prompt_payment_rebate | contracts |
| IRR_RATE | irr_rate | contracts |
| BORROWER_CODE | borrower_code | contracts |
| GUARANTOR_CODE | guarantor_code | contracts |
| AREA_CODE | area_code | contracts |
| REGISTRATION_NUMBER | registration_number | contracts |
| VEHICLE_MAKE | vehicle_make | contracts |
| EQUIPMENT_MODEL | equipment_model | contracts |
| LOAN_CLOSE_DATE | loan_close_date | contracts |
| CATEGORY | category | contracts |
| PDC_LAST_DATE | pdc_last_date | contracts |
| MODE_OF_PAYMENT | mode_of_payment | contracts |
| VEHICLE_AGE | vehicle_age | contracts |
| STATUS | status | contracts |
| ID | id | contracts |
| CREATED_AT | created_at | contracts |
| CREATED_BY | created_by | contracts |
| UPDATED_AT | updated_at | contracts |
| UPDATED_BY | updated_by | contracts |
| IS_ACTIVE | is_active | contracts |

## contract_details

| Oracle Column | PostgreSQL Column | Target Table |
|---|---|---|
| CONTRACT_DETAIL_ID | contract_detail_id | contract_details |
| CONTRACT_ID | contract_id | contract_details |
| EXISTING_LOAN_DETAILS | existing_loan_details | contract_details |
| LINKED_ACCOUNT | linked_account | contract_details |
| PROCESSING_CHARGES | processing_charges | contract_details |
| EMI_ADVANCE | emi_advance | contract_details |
| STATE_CODE | state_code | contract_details |
| ADDITIONAL_COLLATERAL | additional_collateral | contract_details |
| ENGINE_NUMBER | engine_number | contract_details |
| CHASSIS_NUMBER | chassis_number | contract_details |
| REGISTRATION_DATE | registration_date | contract_details |
| LOAN_APPROVED_BY | loan_approved_by | contract_details |
| BORROWER_FI_BY | borrower_fi_by | contract_details |
| VEHICLE_INSPECTION_BY | vehicle_inspection_by | contract_details |
| DOCUMENTS_OBTAINED_BY | documents_obtained_by | contract_details |
| LOAN_REFERRED_BY | loan_referred_by | contract_details |
| OWNER_SERIAL_NUMBER | owner_serial_number | contract_details |
| ID | id | contract_details |
| CREATED_AT | created_at | contract_details |
| CREATED_BY | created_by | contract_details |
| UPDATED_AT | updated_at | contract_details |
| UPDATED_BY | updated_by | contract_details |
| IS_ACTIVE | is_active | contract_details |
