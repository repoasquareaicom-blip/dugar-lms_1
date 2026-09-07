package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.dto.LosContractReceiveRequest;
import dugar_lms_api.modules.contracts.dto.LosProposalDocumentDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class LosContractReceiveRepository {

    private static final String FIND_CONTRACT_BY_LOS_ID_SQL = """
        SELECT contract_id
        FROM contracts
        WHERE los_proposal_id = :losProposalId
        """;

    private static final String FIND_CONTRACT_NUMBER_SQL = """
        SELECT contract_number
        FROM contracts
        WHERE contract_id = :contractId
        """;

    private static final String FIND_USER_ID_BY_USERNAME_SQL = """
        SELECT user_id
        FROM users
        WHERE username = :username
          AND is_active = TRUE
        """;

    private static final String UPSERT_PARTY_SQL = """
        INSERT INTO party_masters (
            party_code,
            party_type,
            customer_type,
            full_name,
            city,
            contact_number,
            email_id,
            date_of_birth,
            pan_number,
            aadhaar_number,
            occupation,
            residence_type,
            distance_km,
            created_by,
            updated_by,
            updated_at,
            is_active
        )
        VALUES (
            :partyCode,
            :partyType,
            'Individual',
            :fullName,
            :city,
            :contactNumber,
            :emailId,
            :dateOfBirth,
            :panNumber,
            :aadhaarNumber,
            :occupation,
            :residenceType,
            :distanceKm,
            :updatedBy,
            :updatedBy,
            CURRENT_TIMESTAMP,
            TRUE
        )
        ON CONFLICT (party_code) DO UPDATE
        SET
            full_name = EXCLUDED.full_name,
            city = EXCLUDED.city,
            contact_number = EXCLUDED.contact_number,
            email_id = EXCLUDED.email_id,
            date_of_birth = EXCLUDED.date_of_birth,
            pan_number = EXCLUDED.pan_number,
            aadhaar_number = EXCLUDED.aadhaar_number,
            occupation = EXCLUDED.occupation,
            residence_type = EXCLUDED.residence_type,
            distance_km = EXCLUDED.distance_km,
            updated_by = EXCLUDED.updated_by,
            updated_at = CURRENT_TIMESTAMP,
            is_active = TRUE
        """;

    private static final String INSERT_CONTRACT_SQL = """
        INSERT INTO contracts (
            contract_type,
            contract_number,
            legacy_contract_number,
            contract_date,
            first_emi_date,
            loan_amount,
            tenure_months,
            flat_interest_rate,
            finance_charges,
            total_contract_value,
            irr_rate,
            borrower_code,
            co_applicant_code,
            area_code,
            registration_number,
            vehicle_make,
            equipment_model,
            category,
            risk_level,
            status,
            is_draft,
            los_received,
            los_proposal_id,
            los_received_at,
            created_by,
            updated_by,
            updated_at,
            is_active
        )
        VALUES (
            :contractType,
            :contractNumber,
            :legacyContractNumber,
            :contractDate,
            :firstEmiDate,
            :loanAmount,
            :tenureMonths,
            :flatInterestRate,
            :financeCharges,
            :totalContractValue,
            :irrRate,
            :borrowerCode,
            :coApplicantCode,
            :areaCode,
            :registrationNumber,
            :vehicleMake,
            :equipmentModel,
            :category,
            :riskLevel,
            'D',
            TRUE,
            TRUE,
            :losProposalId,
            CURRENT_TIMESTAMP,
            :updatedBy,
            :updatedBy,
            CURRENT_TIMESTAMP,
            TRUE
        )
        RETURNING contract_id
        """;

    private static final String UPDATE_CONTRACT_SQL = """
        UPDATE contracts
        SET
            contract_type = :contractType,
            contract_number = :contractNumber,
            legacy_contract_number = NULL,
            contract_date = :contractDate,
            first_emi_date = :firstEmiDate,
            loan_amount = :loanAmount,
            tenure_months = :tenureMonths,
            flat_interest_rate = :flatInterestRate,
            finance_charges = :financeCharges,
            total_contract_value = :totalContractValue,
            irr_rate = :irrRate,
            borrower_code = :borrowerCode,
            co_applicant_code = :coApplicantCode,
            area_code = :areaCode,
            registration_number = :registrationNumber,
            vehicle_make = :vehicleMake,
            equipment_model = :equipmentModel,
            category = :category,
            risk_level = :riskLevel,
            status = CASE
                WHEN UPPER(TRIM(COALESCE(status, ''))) IN ('Y', 'N') THEN TRIM(status)
                WHEN UPPER(TRIM(COALESCE(status, ''))) IN ('E', 'SUBMITTED_FOR_EDIT') THEN 'E'
                ELSE 'D'
            END,
            is_draft = CASE
                WHEN UPPER(TRIM(COALESCE(status, ''))) IN ('Y', 'N') THEN FALSE
                ELSE TRUE
            END,
            los_received = TRUE,
            los_received_at = CURRENT_TIMESTAMP,
            updated_by = :updatedBy,
            updated_at = CURRENT_TIMESTAMP,
            is_active = TRUE
        WHERE contract_id = :contractId
        """;

    private static final String UPSERT_CONTRACT_DETAIL_SQL = """
        INSERT INTO contract_details (
            contract_id,
            processing_charges,
            rto_charges,
            rc_holding_amount,
            other_charges,
            emi_advance,
            payment_done_to,
            payee_1,
            payee_2,
            document_type,
            loan_approved_by,
            tvr_done_by,
            documents_verified_by,
            disbursed_by,
            created_by,
            updated_by,
            updated_at,
            is_active
        )
        VALUES (
            :contractId,
            :processingCharges,
            :rtoCharges,
            :rcHoldingAmount,
            :otherCharges,
            :emiAdvance,
            :paymentDoneTo,
            :payee1,
            :payee2,
            'LOS',
            :loanApprovedBy,
            :tvrDoneBy,
            :documentsVerifiedBy,
            :disbursedBy,
            :updatedBy,
            :updatedBy,
            CURRENT_TIMESTAMP,
            TRUE
        )
        ON CONFLICT (contract_id) DO UPDATE
        SET
            processing_charges = EXCLUDED.processing_charges,
            rto_charges = EXCLUDED.rto_charges,
            rc_holding_amount = EXCLUDED.rc_holding_amount,
            other_charges = EXCLUDED.other_charges,
            emi_advance = EXCLUDED.emi_advance,
            payment_done_to = EXCLUDED.payment_done_to,
            payee_1 = EXCLUDED.payee_1,
            payee_2 = EXCLUDED.payee_2,
            document_type = EXCLUDED.document_type,
            loan_approved_by = EXCLUDED.loan_approved_by,
            tvr_done_by = EXCLUDED.tvr_done_by,
            documents_verified_by = EXCLUDED.documents_verified_by,
            disbursed_by = EXCLUDED.disbursed_by,
            updated_by = EXCLUDED.updated_by,
            updated_at = CURRENT_TIMESTAMP,
            is_active = TRUE
        """;

    private static final String FIND_ASSET_ID_SQL = """
        SELECT asset_id
        FROM assets
        WHERE contract_id = :contractId
        ORDER BY asset_id
        LIMIT 1
        """;

    private static final String INSERT_ASSET_SQL = """
        INSERT INTO assets (
            contract_id,
            product_type,
            finance_type,
            vehicle_type_code,
            registration_number,
            vehicle_make,
            owner_serial_no,
            version,
            manufacture_year,
            fuel_type,
            kms_run,
            equipment_value,
            source_table,
            source_row_hash,
            updated_at
        )
        VALUES (
            :contractId,
            :productType,
            :productType,
            :vehicleTypeCode,
            :registrationNumber,
            :vehicleMake,
            :ownerSerialNo,
            :version,
            :manufactureYear,
            :fuelType,
            :kmsRun,
            :marketValue,
            'LOS_PROPOSALS',
            :sourceRowHash,
            CURRENT_TIMESTAMP
        )
        """;

    private static final String UPDATE_ASSET_SQL = """
        UPDATE assets
        SET
            product_type = :productType,
            finance_type = :productType,
            vehicle_type_code = :vehicleTypeCode,
            registration_number = :registrationNumber,
            vehicle_make = :vehicleMake,
            owner_serial_no = :ownerSerialNo,
            version = :version,
            manufacture_year = :manufactureYear,
            fuel_type = :fuelType,
            kms_run = :kmsRun,
            equipment_value = :marketValue,
            source_table = 'LOS_PROPOSALS',
            source_row_hash = :sourceRowHash,
            updated_at = CURRENT_TIMESTAMP
        WHERE asset_id = :assetId
        """;

    private static final String DELETE_REPAYMENTS_SQL = """
        DELETE FROM contract_repayment_structures
        WHERE contract_id = :contractId
        """;

    private static final String INSERT_REPAYMENT_SQL = """
        INSERT INTO contract_repayment_structures (
            contract_id,
            sequence_no,
            number_of_installments,
            installment_amount,
            updated_at
        )
        VALUES (
            :contractId,
            :sequenceNo,
            :numberOfInstallments,
            :installmentAmount,
            CURRENT_TIMESTAMP
        )
        """;

    private static final String DELETE_UPLOADS_SQL = """
        DELETE FROM contract_document_uploads
        WHERE contract_id = :contractId
          AND (
              document_category LIKE 'LOS:%'
              OR storage_path LIKE 'https://asquareai.com/uploads/%'
              OR storage_path LIKE 'https://dugar.asquareai.com/uploads/%'
          )
        """;

    private static final String INSERT_UPLOAD_SQL = """
        INSERT INTO contract_document_uploads (
            contract_id,
            document_category,
            file_name,
            storage_path,
            created_by,
            updated_by,
            updated_at,
            is_active
        )
        VALUES (
            :contractId,
            :documentCategory,
            :fileName,
            :storagePath,
            :updatedBy,
            :updatedBy,
            CURRENT_TIMESTAMP,
            TRUE
        )
        """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public LosContractReceiveRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Optional<Long> findContractIdByLosProposalId(Long losProposalId) {
        return namedParameterJdbcTemplate.queryForList(
            FIND_CONTRACT_BY_LOS_ID_SQL,
            new MapSqlParameterSource().addValue("losProposalId", losProposalId),
            Long.class
        ).stream().findFirst();
    }

    public String findContractNumber(Long contractId) {
        return namedParameterJdbcTemplate.queryForObject(
            FIND_CONTRACT_NUMBER_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId),
            String.class
        );
    }

    public Optional<Long> findUserIdByUsername(String username) {
        return namedParameterJdbcTemplate.queryForList(
            FIND_USER_ID_BY_USERNAME_SQL,
            new MapSqlParameterSource().addValue("username", username),
            Long.class
        ).stream().findFirst();
    }

    public void upsertBorrower(String partyCode, LosContractReceiveRequest request, String updatedBy) {
        namedParameterJdbcTemplate.update(
            UPSERT_PARTY_SQL,
            new MapSqlParameterSource()
                .addValue("partyCode", partyCode)
                .addValue("partyType", "BORROWER")
                .addValue("fullName", fallback(clean(request.borrowerName()), "-"))
                .addValue("city", clean(request.city()))
                .addValue("contactNumber", clean(request.mobileNumber()))
                .addValue("emailId", clean(request.email()))
                .addValue("dateOfBirth", request.dateOfBirth())
                .addValue("panNumber", clean(request.panNumber()))
                .addValue("aadhaarNumber", clean(request.aadharNumber()))
                .addValue("occupation", clean(request.occupation()))
                .addValue("residenceType", clean(request.houseStatus()))
                .addValue("distanceKm", request.distanceFromOffice())
                .addValue("updatedBy", updatedBy)
        );
    }

    public void upsertCoApplicant(String partyCode, LosContractReceiveRequest request, String updatedBy) {
        namedParameterJdbcTemplate.update(
            UPSERT_PARTY_SQL,
            new MapSqlParameterSource()
                .addValue("partyCode", partyCode)
                .addValue("partyType", "CO_APPLICANT")
                .addValue("fullName", clean(request.coApplicantName()))
                .addValue("city", null)
                .addValue("contactNumber", clean(request.coApplicantMobile()))
                .addValue("emailId", null)
                .addValue("dateOfBirth", null)
                .addValue("panNumber", null)
                .addValue("aadhaarNumber", null)
                .addValue("occupation", clean(request.coApplicantRelationship()))
                .addValue("residenceType", null)
                .addValue("distanceKm", null)
                .addValue("updatedBy", updatedBy)
        );
    }

    public Long insertContract(LosContractReceiveRequest request, String borrowerCode, String coApplicantCode, String updatedBy) {
        return namedParameterJdbcTemplate.queryForObject(
            INSERT_CONTRACT_SQL,
            contractParams(null, request, borrowerCode, coApplicantCode, updatedBy),
            Long.class
        );
    }

    public void updateContract(Long contractId, LosContractReceiveRequest request, String borrowerCode, String coApplicantCode, String updatedBy) {
        namedParameterJdbcTemplate.update(
            UPDATE_CONTRACT_SQL,
            contractParams(contractId, request, borrowerCode, coApplicantCode, updatedBy)
        );
    }

    public void upsertContractDetail(Long contractId, LosContractReceiveRequest request, String updatedBy) {
        namedParameterJdbcTemplate.update(
            UPSERT_CONTRACT_DETAIL_SQL,
            new MapSqlParameterSource()
                .addValue("contractId", contractId)
                .addValue("processingCharges", request.processingCharge())
                .addValue("rtoCharges", request.rtoExpenses())
                .addValue("rcHoldingAmount", request.rcHolding())
                .addValue("otherCharges", request.otherExpenses())
                .addValue("emiAdvance", request.firstEmi())
                .addValue("paymentDoneTo", paymentDoneTo(request))
                .addValue("payee1", clean(request.primaryName()))
                .addValue("payee2", clean(request.secondaryName()))
                .addValue("loanApprovedBy", stringValue(request.approverId()))
                .addValue("tvrDoneBy", clean(request.tvrDoneBy()))
                .addValue("documentsVerifiedBy", clean(request.documentsVerifiedBy()))
                .addValue("disbursedBy", stringValue(request.disbursedBy()))
                .addValue("updatedBy", updatedBy)
        );
    }

    public void upsertAsset(Long contractId, LosContractReceiveRequest request) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractId", contractId)
            .addValue("productType", normalizeProductType(request))
            .addValue("vehicleTypeCode", clean(request.vehicleType()))
            .addValue("registrationNumber", clean(request.rcNumber()))
            .addValue("vehicleMake", clean(request.vehicleName()))
            .addValue("ownerSerialNo", clean(request.ownerNumber()))
            .addValue("version", clean(request.model()))
            .addValue("manufactureYear", clean(request.model()))
            .addValue("fuelType", clean(request.fuelType()))
            .addValue("kmsRun", firstNonNull(request.kilometersDriven(), decimalFromString(request.km())))
            .addValue("marketValue", firstNonNull(request.approvedAmount(), request.loanAmount()))
            .addValue("sourceRowHash", "LOS_PROPOSAL:" + request.id());

        List<Long> assetIds = namedParameterJdbcTemplate.queryForList(FIND_ASSET_ID_SQL, params, Long.class);
        if (assetIds.isEmpty()) {
            namedParameterJdbcTemplate.update(INSERT_ASSET_SQL, params);
            return;
        }

        namedParameterJdbcTemplate.update(UPDATE_ASSET_SQL, params.addValue("assetId", assetIds.get(0)));
    }

    public void replaceRepayments(Long contractId, LosContractReceiveRequest request) {
        namedParameterJdbcTemplate.update(
            DELETE_REPAYMENTS_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId)
        );
        insertRepayment(contractId, 1, request.emiNumber1(), request.emiAmount1());
        insertRepayment(contractId, 2, request.emiNumber2(), request.emiAmount2());
        insertRepayment(contractId, 3, request.emiNumber3(), request.emiAmount3());
    }

    public void replaceDocuments(Long contractId, List<LosProposalDocumentDto> documents, String updatedBy) {
        namedParameterJdbcTemplate.update(
            DELETE_UPLOADS_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId)
        );
        if (documents == null) {
            return;
        }
        for (LosProposalDocumentDto document : documents) {
            if (document == null || isBlank(document.documentType()) || isBlank(document.filePath())) {
                continue;
            }
            String filePath = document.filePath().trim();
            namedParameterJdbcTemplate.update(
                INSERT_UPLOAD_SQL,
                new MapSqlParameterSource()
                    .addValue("contractId", contractId)
                    .addValue("documentCategory", documentCategory(document))
                    .addValue("fileName", fileName(filePath))
                    .addValue("storagePath", lmsUploadPath(filePath))
                    .addValue("updatedBy", updatedBy)
            );
        }
    }

    private String documentCategory(LosProposalDocumentDto document) {
        String category = clean(document.category());
        if (category != null) {
            return category;
        }
        return document.documentType().trim();
    }

    private void insertRepayment(Long contractId, int sequenceNo, Integer installments, BigDecimal amount) {
        if (installments == null || installments <= 0 || amount == null || amount.signum() <= 0) {
            return;
        }
        namedParameterJdbcTemplate.update(
            INSERT_REPAYMENT_SQL,
            new MapSqlParameterSource()
                .addValue("contractId", contractId)
                .addValue("sequenceNo", sequenceNo)
                .addValue("numberOfInstallments", installments)
                .addValue("installmentAmount", amount)
        );
    }

    private MapSqlParameterSource contractParams(
        Long contractId,
        LosContractReceiveRequest request,
        String borrowerCode,
        String coApplicantCode,
        String updatedBy
    ) {
        String contractNumber = contractNumber(request);
        BigDecimal loanAmount = firstNonNull(request.sanctionAmount(), request.approvedAmount(), request.loanAmount());
        BigDecimal totalContractValue = totalEmiAmount(request);
        LocalDate contractDate = firstNonNull(request.disbursedDate(), localDate(request.createdAt()));
        return new MapSqlParameterSource()
            .addValue("contractId", contractId)
            .addValue("contractType", fallback(normalizeProductType(request), "HP"))
            .addValue("contractNumber", contractNumber)
            .addValue("legacyContractNumber", null)
            .addValue("contractDate", contractDate)
            .addValue("firstEmiDate", firstEmiDate(contractDate, request.firstEmi()))
            .addValue("loanAmount", loanAmount)
            .addValue("tenureMonths", tenureMonths(request))
            .addValue("flatInterestRate", request.interestRate())
            .addValue("financeCharges", financeCharges(totalContractValue, loanAmount))
            .addValue("totalContractValue", totalContractValue)
            .addValue("irrRate", request.irr())
            .addValue("borrowerCode", borrowerCode)
            .addValue("coApplicantCode", coApplicantCode)
            .addValue("areaCode", firstNonBlank(request.arUsername(), stringValue(request.arUserId())))
            .addValue("registrationNumber", clean(request.rcNumber()))
            .addValue("vehicleMake", clean(request.vehicleName()))
            .addValue("equipmentModel", clean(request.model()))
            .addValue("category", selectedOrDash(request.approvedCategory()))
            .addValue("riskLevel", riskLevel(request.riskLevel()))
            .addValue("losProposalId", request.id())
            .addValue("updatedBy", updatedBy);
    }

    private String riskLevel(String value) {
        String cleaned = selectedOrDash(value);
        if ("-".equals(cleaned)) {
            return "-";
        }
        return switch (cleaned.trim().toUpperCase()) {
            case "HIGH" -> "High";
            case "MEDIUM" -> "Medium";
            case "LOW" -> "Low";
            default -> cleaned;
        };
    }

    private LocalDate firstEmiDate(LocalDate contractDate, BigDecimal firstEmi) {
        if (contractDate == null) {
            return null;
        }
        if (firstEmi != null && firstEmi.signum() > 0) {
            return contractDate;
        }
        return contractDate.plusMonths(1);
    }

    private String contractNumber(LosContractReceiveRequest request) {
        String hpNumber = clean(request.hpNumber());
        if (hpNumber != null) {
            return hpNumber;
        }
        String referenceNumber = clean(request.referenceNumber());
        if (referenceNumber != null) {
            return referenceNumber;
        }
        return "LOS" + String.format("%06d", request.id());
    }

    private String paymentDoneTo(LosContractReceiveRequest request) {
        String primaryBank = clean(request.primaryBank());
        String primaryAcNumber = clean(request.primaryAcNumber());
        if (primaryBank != null && primaryAcNumber != null) {
            return primaryBank + " " + primaryAcNumber;
        }
        if (primaryBank != null) {
            return primaryBank;
        }
        if (primaryAcNumber != null) {
            return primaryAcNumber;
        }
        return null;
    }

    private String normalizeProductType(LosContractReceiveRequest request) {
        String value = clean(request.productType());
        if (value == null) {
            return hasVehicleDetails(request) ? "Vehicles" : null;
        }

        String normalized = value.toLowerCase().replaceAll("[\\s_-]+", "");
        return switch (normalized) {
            case "vehicle", "vehicles", "car", "auto", "twowheeler", "threewheeler", "vehicleloan" -> "Vehicles";
            case "businessloan", "businessloans" -> "Business Loans";
            case "msme" -> "MSME";
            case "lap" -> "LAP";
            case "collateral" -> "Collateral";
            default -> hasVehicleDetails(request) ? "Vehicles" : value;
        };
    }

    private boolean hasVehicleDetails(LosContractReceiveRequest request) {
        return clean(request.vehicleName()) != null
            || clean(request.rcNumber()) != null
            || clean(request.vehicleType()) != null
            || clean(request.ownerNumber()) != null
            || request.kilometersDriven() != null;
    }

    private String selectedOrDash(String value) {
        String cleaned = clean(value);
        return cleaned == null || "select".equalsIgnoreCase(cleaned) || "0".equals(cleaned) ? "-" : cleaned;
    }

    private Integer tenureMonths(LosContractReceiveRequest request) {
        int tenure = 0;
        tenure += positiveOrZero(request.emiNumber1());
        tenure += positiveOrZero(request.emiNumber2());
        tenure += positiveOrZero(request.emiNumber3());
        return tenure == 0 ? null : tenure;
    }

    private BigDecimal totalEmiAmount(LosContractReceiveRequest request) {
        BigDecimal total = BigDecimal.ZERO;
        total = total.add(emiTotal(request.emiNumber1(), request.emiAmount1()));
        total = total.add(emiTotal(request.emiNumber2(), request.emiAmount2()));
        total = total.add(emiTotal(request.emiNumber3(), request.emiAmount3()));
        return total.signum() == 0 ? null : total;
    }

    private BigDecimal emiTotal(Integer installments, BigDecimal amount) {
        if (installments == null || installments <= 0 || amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(installments.longValue()));
    }

    private BigDecimal financeCharges(BigDecimal totalContractValue, BigDecimal loanAmount) {
        if (totalContractValue == null || loanAmount == null) {
            return null;
        }
        return totalContractValue.subtract(loanAmount);
    }

    private int positiveOrZero(Integer value) {
        return value == null || value <= 0 ? 0 : value;
    }

    private LocalDate localDate(java.time.LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    private String fileName(String filePath) {
        String normalized = filePath.replace('\\', '/');
        int lastSlashIndex = normalized.lastIndexOf('/');
        return lastSlashIndex >= 0 ? normalized.substring(lastSlashIndex + 1) : normalized;
    }

    private String lmsUploadPath(String filePath) {
        return "https://dugar.asquareai.com/uploads/" + fileName(filePath);
    }

    private BigDecimal decimalFromString(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String fallback(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
