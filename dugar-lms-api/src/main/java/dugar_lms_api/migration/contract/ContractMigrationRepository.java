package dugar_lms_api.migration.contract;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Repository
public class ContractMigrationRepository {

    private static final String EXISTS_CONTRACT_SQL = """
        SELECT EXISTS (
            SELECT 1
            FROM contracts
            WHERE contract_type = :contractType
              AND contract_number = :contractNumber
        )
        """;

    private static final String INSERT_CONTRACT_SQL = """
        INSERT INTO contracts (
            contract_type,
            contract_number,
            legacy_contract_number,
            contract_date,
            loan_amount,
            first_emi_date,
            tenure_months,
            flat_interest_rate,
            insurance_deposit,
            finance_charges,
            bpfc_days,
            bpfc_rate,
            bpfc_amount,
            total_contract_value,
            prompt_payment_rebate,
            irr_rate,
            borrower_code,
            guarantor_code,
            area_code,
            registration_number,
            vehicle_make,
            equipment_model,
            loan_close_date,
            category,
            pdc_last_date,
            mode_of_payment,
            repayment_terms,
            moratorium_months,
            payment_frequency,
            repayment_type,
            enach_applicable,
            vehicle_age,
            status,
            created_by,
            is_active
        )
        VALUES (
            :contractType,
            :contractNumber,
            :legacyContractNumber,
            :contractDate,
            :loanAmount,
            :firstEmiDate,
            :tenureMonths,
            :flatInterestRate,
            :insuranceDeposit,
            :financeCharges,
            :bpfcDays,
            :bpfcRate,
            :bpfcAmount,
            :totalContractValue,
            :promptPaymentRebate,
            :irrRate,
            :borrowerCode,
            :guarantorCode,
            :areaCode,
            :registrationNumber,
            :vehicleMake,
            :equipmentModel,
            :loanCloseDate,
            :category,
            :pdcLastDate,
            :modeOfPayment,
            :repaymentTerms,
            :moratoriumMonths,
            :paymentFrequency,
            :repaymentType,
            :enachApplicable,
            :vehicleAge,
            :status,
            :createdBy,
            :isActive
        )
        RETURNING contract_id
        """;

    private static final String INSERT_CONTRACT_DETAILS_SQL = """
        INSERT INTO contract_details (
            contract_id,
            existing_loan_details,
            linked_account,
            processing_charges,
            emi_advance,
            state_code,
            additional_collateral,
            engine_number,
            chassis_number,
            registration_date,
            document_type,
            document_verified_by,
            loan_approved_by,
            borrower_fi_by,
            guarantor_fi_by,
            tvr_done_by,
            vehicle_by_agency,
            vehicle_inspection_by,
            property_valuation_by,
            legal_opinion_by,
            branch_collection_tool_by,
            geo_coordinate_1,
            geo_coordinate_2,
            documents_obtained_by,
            documents_checked_by,
            documents_verified_by,
            loan_referred_by,
            disbursed_by,
            rc_online_checking,
            ho_collection_tool_by,
            ho_tvr_done_by,
            stock_marked_to_bank,
            owner_serial_number,
            stamp_duty,
            rto_charges,
            valuation_charges,
            rc_holding_amount,
            other_charges,
            payment_done_to,
            payee_1,
            payee_2,
            payee_3,
            created_by,
            is_active
        )
        VALUES (
            :contractId,
            :existingLoanDetails,
            :linkedAccount,
            :processingCharges,
            :emiAdvance,
            :stateCode,
            :additionalCollateral,
            :engineNumber,
            :chassisNumber,
            :registrationDate,
            :documentType,
            :documentVerifiedBy,
            :loanApprovedBy,
            :borrowerFiBy,
            :guarantorFiBy,
            :tvrDoneBy,
            :vehicleByAgency,
            :vehicleInspectionBy,
            :propertyValuationBy,
            :legalOpinionBy,
            :branchCollectionToolBy,
            :geoCoordinate1,
            :geoCoordinate2,
            :documentsObtainedBy,
            :documentsCheckedBy,
            :documentsVerifiedBy,
            :loanReferredBy,
            :disbursedBy,
            :rcOnlineChecking,
            :hoCollectionToolBy,
            :hoTvrDoneBy,
            :stockMarkedToBank,
            :ownerSerialNumber,
            :stampDuty,
            :rtoCharges,
            :valuationCharges,
            :rcHoldingAmount,
            :otherCharges,
            :paymentDoneTo,
            :payee1,
            :payee2,
            :payee3,
            :createdBy,
            :isActive
        )
        """;

    private static final String TABLE_COLUMNS_SQL = """
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = :tableName
        """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ContractMigrationRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public boolean contractExists(String contractType, String contractNumber) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractType", contractType)
            .addValue("contractNumber", contractNumber);

        Boolean exists = namedParameterJdbcTemplate.queryForObject(EXISTS_CONTRACT_SQL, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public Long insertContract(ContractInsert contract) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractType", contract.contractType())
            .addValue("contractNumber", contract.contractNumber())
            .addValue("legacyContractNumber", contract.legacyContractNumber())
            .addValue("contractDate", contract.contractDate())
            .addValue("loanAmount", contract.loanAmount())
            .addValue("firstEmiDate", contract.firstEmiDate())
            .addValue("tenureMonths", contract.tenureMonths())
            .addValue("flatInterestRate", contract.flatInterestRate())
            .addValue("insuranceDeposit", contract.insuranceDeposit())
            .addValue("financeCharges", contract.financeCharges())
            .addValue("bpfcDays", contract.bpfcDays())
            .addValue("bpfcRate", contract.bpfcRate())
            .addValue("bpfcAmount", contract.bpfcAmount())
            .addValue("totalContractValue", contract.totalContractValue())
            .addValue("promptPaymentRebate", contract.promptPaymentRebate())
            .addValue("irrRate", contract.irrRate())
            .addValue("borrowerCode", contract.borrowerCode())
            .addValue("guarantorCode", contract.guarantorCode())
            .addValue("areaCode", contract.areaCode())
            .addValue("registrationNumber", contract.registrationNumber())
            .addValue("vehicleMake", contract.vehicleMake())
            .addValue("equipmentModel", contract.equipmentModel())
            .addValue("loanCloseDate", contract.loanCloseDate())
            .addValue("category", contract.category())
            .addValue("pdcLastDate", contract.pdcLastDate())
            .addValue("modeOfPayment", contract.modeOfPayment())
            .addValue("repaymentTerms", contract.repaymentTerms())
            .addValue("moratoriumMonths", contract.moratoriumMonths())
            .addValue("paymentFrequency", contract.paymentFrequency())
            .addValue("repaymentType", contract.repaymentType())
            .addValue("enachApplicable", contract.enachApplicable())
            .addValue("vehicleAge", contract.vehicleAge())
            .addValue("status", contract.status())
            .addValue("createdBy", contract.createdBy())
            .addValue("isActive", contract.isActive());

        return namedParameterJdbcTemplate.queryForObject(INSERT_CONTRACT_SQL, params, Long.class);
    }

    public void insertContractDetails(ContractDetailsInsert details) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("contractId", details.contractId())
            .addValue("existingLoanDetails", details.existingLoanDetails())
            .addValue("linkedAccount", details.linkedAccount())
            .addValue("processingCharges", details.processingCharges())
            .addValue("emiAdvance", details.emiAdvance())
            .addValue("stateCode", details.stateCode())
            .addValue("additionalCollateral", details.additionalCollateral())
            .addValue("engineNumber", details.engineNumber())
            .addValue("chassisNumber", details.chassisNumber())
            .addValue("registrationDate", details.registrationDate())
            .addValue("documentType", details.documentType())
            .addValue("documentVerifiedBy", details.documentVerifiedBy())
            .addValue("loanApprovedBy", details.loanApprovedBy())
            .addValue("borrowerFiBy", details.borrowerFiBy())
            .addValue("guarantorFiBy", details.guarantorFiBy())
            .addValue("tvrDoneBy", details.tvrDoneBy())
            .addValue("vehicleByAgency", details.vehicleByAgency())
            .addValue("vehicleInspectionBy", details.vehicleInspectionBy())
            .addValue("propertyValuationBy", details.propertyValuationBy())
            .addValue("legalOpinionBy", details.legalOpinionBy())
            .addValue("branchCollectionToolBy", details.branchCollectionToolBy())
            .addValue("geoCoordinate1", details.geoCoordinate1())
            .addValue("geoCoordinate2", details.geoCoordinate2())
            .addValue("documentsObtainedBy", details.documentsObtainedBy())
            .addValue("documentsCheckedBy", details.documentsCheckedBy())
            .addValue("documentsVerifiedBy", details.documentsVerifiedBy())
            .addValue("loanReferredBy", details.loanReferredBy())
            .addValue("disbursedBy", details.disbursedBy())
            .addValue("rcOnlineChecking", details.rcOnlineChecking())
            .addValue("hoCollectionToolBy", details.hoCollectionToolBy())
            .addValue("hoTvrDoneBy", details.hoTvrDoneBy())
            .addValue("stockMarkedToBank", details.stockMarkedToBank())
            .addValue("ownerSerialNumber", details.ownerSerialNumber())
            .addValue("stampDuty", details.stampDuty())
            .addValue("rtoCharges", details.rtoCharges())
            .addValue("valuationCharges", details.valuationCharges())
            .addValue("rcHoldingAmount", details.rcHoldingAmount())
            .addValue("otherCharges", details.otherCharges())
            .addValue("paymentDoneTo", details.paymentDoneTo())
            .addValue("payee1", details.payee1())
            .addValue("payee2", details.payee2())
            .addValue("payee3", details.payee3())
            .addValue("createdBy", details.createdBy())
            .addValue("isActive", details.isActive());

        namedParameterJdbcTemplate.update(INSERT_CONTRACT_DETAILS_SQL, params);
    }

    public Set<String> tableColumns(String tableName) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tableName", tableName);

        return new LinkedHashSet<>(namedParameterJdbcTemplate.queryForList(TABLE_COLUMNS_SQL, params, String.class));
    }

    public record ContractInsert(
        String contractType,
        String contractNumber,
        String legacyContractNumber,
        LocalDate contractDate,
        BigDecimal loanAmount,
        LocalDate firstEmiDate,
        Integer tenureMonths,
        BigDecimal flatInterestRate,
        BigDecimal insuranceDeposit,
        BigDecimal financeCharges,
        Integer bpfcDays,
        BigDecimal bpfcRate,
        BigDecimal bpfcAmount,
        BigDecimal totalContractValue,
        BigDecimal promptPaymentRebate,
        BigDecimal irrRate,
        String borrowerCode,
        String guarantorCode,
        String areaCode,
        String registrationNumber,
        String vehicleMake,
        String equipmentModel,
        LocalDate loanCloseDate,
        String category,
        LocalDate pdcLastDate,
        String modeOfPayment,
        String repaymentTerms,
        Integer moratoriumMonths,
        String paymentFrequency,
        String repaymentType,
        Boolean enachApplicable,
        Integer vehicleAge,
        String status,
        String createdBy,
        boolean isActive
    ) {
    }

    public record ContractDetailsInsert(
        Long contractId,
        String existingLoanDetails,
        String linkedAccount,
        BigDecimal processingCharges,
        BigDecimal emiAdvance,
        String stateCode,
        String additionalCollateral,
        String engineNumber,
        String chassisNumber,
        LocalDate registrationDate,
        String documentType,
        String documentVerifiedBy,
        String loanApprovedBy,
        String borrowerFiBy,
        String guarantorFiBy,
        String tvrDoneBy,
        String vehicleByAgency,
        String vehicleInspectionBy,
        String propertyValuationBy,
        String legalOpinionBy,
        String branchCollectionToolBy,
        String geoCoordinate1,
        String geoCoordinate2,
        String documentsObtainedBy,
        String documentsCheckedBy,
        String documentsVerifiedBy,
        String loanReferredBy,
        String disbursedBy,
        String rcOnlineChecking,
        String hoCollectionToolBy,
        String hoTvrDoneBy,
        String stockMarkedToBank,
        String ownerSerialNumber,
        BigDecimal stampDuty,
        BigDecimal rtoCharges,
        BigDecimal valuationCharges,
        BigDecimal rcHoldingAmount,
        BigDecimal otherCharges,
        String paymentDoneTo,
        String payee1,
        String payee2,
        String payee3,
        String createdBy,
        boolean isActive
    ) {
    }
}
