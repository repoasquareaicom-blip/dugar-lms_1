package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.dto.ContractFinancialDraftDto;
import dugar_lms_api.modules.contracts.dto.ContractRepaymentStructureDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ContractFinancialDraftRepository {

    private static final String FIND_FINANCIAL_SQL = """
        SELECT
            c.contract_id,
            c.loan_amount,
            c.tenure_months,
            c.flat_interest_rate,
            c.irr_rate,
            c.insurance_deposit,
            c.total_contract_value,
            c.repayment_terms,
            c.is_first_emi_paid,
            c.first_emi_date,
            c.moratorium_months,
            c.repayment_type,
            cd.emi_advance,
            cd.processing_charges,
            cd.rto_charges,
            cd.valuation_charges,
            cd.stamp_duty,
            cd.rc_holding_amount,
            cd.other_charges,
            c.mode_of_payment,
            cd.payment_done_to,
            cd.payee_1,
            cd.payee_2,
            cd.payee_3
        FROM contracts c
        LEFT JOIN contract_details cd
          ON cd.contract_id = c.contract_id
        WHERE c.contract_id = :contractId
        ORDER BY cd.contract_detail_id
        LIMIT 1
        """;

    private static final String FIND_REPAYMENTS_SQL = """
        SELECT
            sequence_no,
            number_of_installments,
            installment_amount
        FROM contract_repayment_structures
        WHERE contract_id = :contractId
        ORDER BY sequence_no
        """;

    private static final String FIND_CONTRACT_DATE_SQL = """
        SELECT contract_date
        FROM contracts
        WHERE contract_id = :contractId
        """;

    private static final String FIND_CONTRACT_NUMBER_SQL = """
        SELECT contract_number
        FROM contracts
        WHERE contract_id = :contractId
        """;

    private static final String RECALCULATE_CONTRACT_IRR_SQL = """
        CALL public.sp_recalculate_contract_irr(:contractNumber)
        """;

    private static final String UPDATE_CONTRACT_SQL = """
        UPDATE contracts
        SET
            loan_amount = :loanAmount,
            tenure_months = :tenureMonths,
            flat_interest_rate = :flatInterestRate,
            irr_rate = :irrRate,
            insurance_deposit = :insuranceDeposit,
            total_contract_value = :totalContractValue,
            repayment_terms = :repaymentTerms,
            is_first_emi_paid = :isFirstEmiPaid,
            first_emi_date = :firstEmiDate,
            moratorium_months = :moratoriumMonths,
            repayment_type = :repaymentType,
            mode_of_payment = :modeOfPayment,
            updated_by = :updatedBy,
            updated_at = CURRENT_TIMESTAMP
        WHERE contract_id = :contractId
        """;

    private static final String FIND_CONTRACT_DETAIL_ID_SQL = """
        SELECT contract_detail_id
        FROM contract_details
        WHERE contract_id = :contractId
        ORDER BY contract_detail_id
        LIMIT 1
        """;

    private static final String INSERT_CONTRACT_DETAIL_SQL = """
        INSERT INTO contract_details (
            contract_id,
            emi_advance,
            processing_charges,
            rto_charges,
            valuation_charges,
            stamp_duty,
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
            :emiAdvance,
            :processingCharges,
            :rtoCharges,
            :valuationCharges,
            :stampDuty,
            :rcHoldingAmount,
            :otherCharges,
            :paymentDoneTo,
            :payee1,
            :payee2,
            :payee3,
            :updatedBy,
            TRUE
        )
        """;

    private static final String UPDATE_CONTRACT_DETAIL_SQL = """
        UPDATE contract_details
        SET
            emi_advance = :emiAdvance,
            processing_charges = :processingCharges,
            rto_charges = :rtoCharges,
            valuation_charges = :valuationCharges,
            stamp_duty = :stampDuty,
            rc_holding_amount = :rcHoldingAmount,
            other_charges = :otherCharges,
            payment_done_to = :paymentDoneTo,
            payee_1 = :payee1,
            payee_2 = :payee2,
            payee_3 = :payee3,
            updated_by = :updatedBy,
            updated_at = CURRENT_TIMESTAMP,
            is_active = TRUE
        WHERE contract_detail_id = :contractDetailId
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

    private static final RowMapper<ContractFinancialDraftDto> FINANCIAL_ROW_MAPPER = (rs, rowNum) -> new ContractFinancialDraftDto(
        rs.getLong("contract_id"),
        rs.getBigDecimal("loan_amount"),
        rs.getObject("tenure_months", Integer.class),
        rs.getBigDecimal("flat_interest_rate"),
        rs.getBigDecimal("irr_rate"),
        rs.getBigDecimal("insurance_deposit"),
        rs.getBigDecimal("total_contract_value"),
        rs.getString("repayment_terms"),
        rs.getBoolean("is_first_emi_paid"),
        rs.getObject("first_emi_date", java.time.LocalDate.class),
        rs.getObject("moratorium_months", Integer.class),
        rs.getString("repayment_type"),
        rs.getBigDecimal("emi_advance"),
        rs.getBigDecimal("processing_charges"),
        rs.getBigDecimal("rto_charges"),
        rs.getBigDecimal("valuation_charges"),
        rs.getBigDecimal("stamp_duty"),
        rs.getBigDecimal("rc_holding_amount"),
        rs.getBigDecimal("other_charges"),
        rs.getString("mode_of_payment"),
        rs.getString("payment_done_to"),
        rs.getString("payee_1"),
        rs.getString("payee_2"),
        rs.getString("payee_3"),
        List.of()
    );

    private static final RowMapper<ContractRepaymentStructureDto> REPAYMENT_ROW_MAPPER = (rs, rowNum) -> new ContractRepaymentStructureDto(
        rs.getObject("sequence_no", Integer.class),
        rs.getObject("number_of_installments", Integer.class),
        rs.getBigDecimal("installment_amount")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ContractFinancialDraftRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Optional<ContractFinancialDraftDto> findByContractId(Long contractId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("contractId", contractId);
        List<ContractFinancialDraftDto> rows = namedParameterJdbcTemplate.query(FIND_FINANCIAL_SQL, params, FINANCIAL_ROW_MAPPER);
        return rows.stream().findFirst().map((financial) -> withRepayments(financial, findRepayments(contractId)));
    }

    public List<ContractRepaymentStructureDto> findRepayments(Long contractId) {
        return namedParameterJdbcTemplate.query(
            FIND_REPAYMENTS_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId),
            REPAYMENT_ROW_MAPPER
        );
    }

    public java.time.LocalDate findContractDate(Long contractId) {
        return namedParameterJdbcTemplate.queryForObject(
            FIND_CONTRACT_DATE_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId),
            java.time.LocalDate.class
        );
    }

    public void recalculateContractIrr(Long contractId) {
        String contractNumber = namedParameterJdbcTemplate.queryForObject(
            FIND_CONTRACT_NUMBER_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId),
            String.class
        );
        if (contractNumber == null || contractNumber.isBlank()) {
            return;
        }
        namedParameterJdbcTemplate.update(
            RECALCULATE_CONTRACT_IRR_SQL,
            new MapSqlParameterSource().addValue("contractNumber", contractNumber.trim())
        );
    }

    public void updateContract(ContractFinancialDraftDto financial, String updatedBy) {
        namedParameterJdbcTemplate.update(UPDATE_CONTRACT_SQL, params(financial).addValue("updatedBy", updatedBy));
    }

    public void upsertContractDetail(ContractFinancialDraftDto financial, String updatedBy) {
        MapSqlParameterSource lookupParams = new MapSqlParameterSource().addValue("contractId", financial.contractId());
        List<Long> detailIds = namedParameterJdbcTemplate.queryForList(
            FIND_CONTRACT_DETAIL_ID_SQL,
            lookupParams,
            Long.class
        );

        MapSqlParameterSource params = params(financial).addValue("updatedBy", updatedBy);
        if (detailIds.isEmpty()) {
            namedParameterJdbcTemplate.update(INSERT_CONTRACT_DETAIL_SQL, params);
            return;
        }

        namedParameterJdbcTemplate.update(
            UPDATE_CONTRACT_DETAIL_SQL,
            params.addValue("contractDetailId", detailIds.get(0))
        );
    }

    public void replaceRepayments(Long contractId, List<ContractRepaymentStructureDto> repayments) {
        namedParameterJdbcTemplate.update(
            DELETE_REPAYMENTS_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId)
        );

        if (repayments == null) {
            return;
        }

        for (int index = 0; index < repayments.size(); index++) {
            ContractRepaymentStructureDto repayment = repayments.get(index);
            if (repayment == null || repayment.numberOfInstallments() == null || repayment.installmentAmount() == null) {
                continue;
            }

            namedParameterJdbcTemplate.update(
                INSERT_REPAYMENT_SQL,
                new MapSqlParameterSource()
                    .addValue("contractId", contractId)
                    .addValue("sequenceNo", index + 1)
                    .addValue("numberOfInstallments", repayment.numberOfInstallments())
                    .addValue("installmentAmount", repayment.installmentAmount())
            );
        }
    }

    public ContractFinancialDraftDto withRepayments(
        ContractFinancialDraftDto financial,
        List<ContractRepaymentStructureDto> repayments
    ) {
        return new ContractFinancialDraftDto(
            financial.contractId(),
            financial.loanAmount(),
            financial.tenureMonths(),
            financial.flatInterestRate(),
            financial.irrRate(),
            financial.insuranceDeposit(),
            financial.totalContractValue(),
            financial.repaymentTerms(),
            Boolean.TRUE.equals(financial.isFirstEmiPaid()),
            financial.firstEmiDate(),
            financial.moratoriumMonths(),
            financial.repaymentType(),
            financial.emiAdvance(),
            financial.processingCharges(),
            financial.rtoCharges(),
            financial.valuationCharges(),
            financial.stampDuty(),
            financial.rcHoldingAmount(),
            financial.otherCharges(),
            financial.modeOfPayment(),
            financial.paymentDoneTo(),
            financial.payee1(),
            financial.payee2(),
            financial.payee3(),
            repayments
        );
    }

    private MapSqlParameterSource params(ContractFinancialDraftDto financial) {
        return new MapSqlParameterSource()
            .addValue("contractId", financial.contractId())
            .addValue("loanAmount", financial.loanAmount())
            .addValue("tenureMonths", financial.tenureMonths())
            .addValue("flatInterestRate", financial.flatInterestRate())
            .addValue("irrRate", financial.irrRate())
            .addValue("insuranceDeposit", financial.insuranceDeposit())
            .addValue("totalContractValue", financial.totalContractValue())
            .addValue("repaymentTerms", clean(financial.repaymentTerms()))
            .addValue("isFirstEmiPaid", Boolean.TRUE.equals(financial.isFirstEmiPaid()))
            .addValue("firstEmiDate", financial.firstEmiDate())
            .addValue("moratoriumMonths", financial.moratoriumMonths())
            .addValue("repaymentType", clean(financial.repaymentType()))
            .addValue("emiAdvance", financial.emiAdvance())
            .addValue("processingCharges", financial.processingCharges())
            .addValue("rtoCharges", financial.rtoCharges())
            .addValue("valuationCharges", financial.valuationCharges())
            .addValue("stampDuty", financial.stampDuty())
            .addValue("rcHoldingAmount", financial.rcHoldingAmount())
            .addValue("otherCharges", financial.otherCharges())
            .addValue("modeOfPayment", clean(financial.modeOfPayment()))
            .addValue("paymentDoneTo", clean(financial.paymentDoneTo()))
            .addValue("payee1", clean(financial.payee1()))
            .addValue("payee2", clean(financial.payee2()))
            .addValue("payee3", clean(financial.payee3()));
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
