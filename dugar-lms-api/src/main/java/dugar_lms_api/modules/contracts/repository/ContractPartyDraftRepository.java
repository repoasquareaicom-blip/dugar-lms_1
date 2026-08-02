package dugar_lms_api.modules.contracts.repository;

import dugar_lms_api.modules.contracts.dto.PartyDraftDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ContractPartyDraftRepository {

    private static final String NEXT_PARTY_ID_SQL = "SELECT nextval(pg_get_serial_sequence('party_masters', 'id'))";
    private static final String NEXT_CONTRACT_ID_SQL = "SELECT nextval(pg_get_serial_sequence('contracts', 'contract_id'))";
    private static final String NEXT_CONTRACT_AUDIT_ID_SQL = "SELECT nextval(pg_get_serial_sequence('contracts', 'id'))";

    private static final String EXISTS_SQL = """
        SELECT COUNT(*)
        FROM party_masters
        WHERE party_code = :partyCode
        """;

    private static final String INSERT_SQL = """
        INSERT INTO party_masters (
            id,
            party_code,
            party_type,
            customer_type,
            salutation,
            full_name,
            firm_name,
            partner_1_name,
            partner_2_name,
            swd_name,
            address_line_1,
            address_line_2,
            area,
            city,
            state,
            pin_code,
            contact_number,
            alternative_number,
            email_id,
            date_of_birth,
            pan_number,
            aadhaar_number,
            occupation,
            annual_income,
            residence_type,
            distance_km,
            created_by,
            is_active
        )
        VALUES (
            :id,
            :partyCode,
            :partyType,
            :customerType,
            :salutation,
            :fullName,
            :firmName,
            :partner1Name,
            :partner2Name,
            :swdName,
            :addressLine1,
            :addressLine2,
            :area,
            :city,
            :state,
            :pinCode,
            :contactNumber,
            :alternativeNumber,
            :emailId,
            :dateOfBirth,
            :panNumber,
            :aadhaarNumber,
            :occupation,
            :annualIncome,
            :residenceType,
            :distanceKm,
            :updatedBy,
            TRUE
        )
        """;

    private static final String UPDATE_SQL = """
        UPDATE party_masters
        SET
            customer_type = :customerType,
            salutation = :salutation,
            full_name = :fullName,
            firm_name = :firmName,
            partner_1_name = :partner1Name,
            partner_2_name = :partner2Name,
            swd_name = :swdName,
            address_line_1 = :addressLine1,
            address_line_2 = :addressLine2,
            area = :area,
            city = :city,
            state = :state,
            pin_code = :pinCode,
            contact_number = :contactNumber,
            alternative_number = :alternativeNumber,
            email_id = :emailId,
            date_of_birth = :dateOfBirth,
            pan_number = :panNumber,
            aadhaar_number = :aadhaarNumber,
            occupation = :occupation,
            annual_income = :annualIncome,
            residence_type = :residenceType,
            distance_km = :distanceKm,
            updated_by = :updatedBy,
            updated_at = CURRENT_TIMESTAMP,
            is_active = TRUE
        WHERE party_code = :partyCode
        """;

    private static final String INSERT_CONTRACT_DRAFT_SQL = """
        INSERT INTO contracts (
            contract_id,
            id,
            contract_type,
            contract_number,
            legacy_contract_number,
            borrower_code,
            co_applicant_code,
            guarantor_code,
            guarantor_2_code,
            status,
            is_draft,
            created_by,
            is_active
        )
        VALUES (
            :contractId,
            :auditId,
            'HP',
            :contractNumber,
            :contractNumber,
            :borrowerCode,
            :coApplicantCode,
            :guarantorCode,
            :guarantor2Code,
            'DRAFT',
            TRUE,
            :updatedBy,
            TRUE
        )
        """;

    private static final String UPDATE_CONTRACT_DRAFT_SQL = """
        UPDATE contracts
        SET
            borrower_code = :borrowerCode,
            co_applicant_code = :coApplicantCode,
            guarantor_code = :guarantorCode,
            guarantor_2_code = :guarantor2Code,
            status = CASE
                WHEN UPPER(COALESCE(status, '')) IN ('Y', 'SUBMITTED_FOR_EDIT') THEN status
                ELSE 'DRAFT'
            END,
            is_draft = CASE
                WHEN UPPER(COALESCE(status, '')) = 'Y' THEN FALSE
                ELSE TRUE
            END,
            updated_by = :updatedBy,
            updated_at = CURRENT_TIMESTAMP
        WHERE contract_id = :contractId
        """;

    private static final String FIND_CONTRACT_NUMBER_SQL = """
        SELECT contract_number
        FROM contracts
        WHERE contract_id = :contractId
        """;

    private static final String FIND_CONTRACT_PARTIES_SQL = """
        WITH contract_party_codes AS (
            SELECT 'applicant' AS role, borrower_code AS party_code, 1 AS sort_order
            FROM contracts
            WHERE contract_id = :contractId
            UNION ALL
            SELECT 'coApplicant' AS role, co_applicant_code AS party_code, 2 AS sort_order
            FROM contracts
            WHERE contract_id = :contractId
            UNION ALL
            SELECT 'guarantor1' AS role, guarantor_code AS party_code, 3 AS sort_order
            FROM contracts
            WHERE contract_id = :contractId
            UNION ALL
            SELECT 'guarantor2' AS role, guarantor_2_code AS party_code, 4 AS sort_order
            FROM contracts
            WHERE contract_id = :contractId
        )
        SELECT
            cpc.role,
            pm.party_code,
            pm.customer_type,
            pm.salutation,
            pm.full_name,
            pm.firm_name,
            pm.partner_1_name,
            pm.partner_2_name,
            pm.swd_name,
            pm.address_line_1,
            pm.address_line_2,
            pm.area,
            pm.city,
            pm.state,
            pm.pin_code,
            pm.contact_number,
            pm.alternative_number,
            pm.email_id,
            pm.date_of_birth,
            pm.pan_number,
            pm.aadhaar_number,
            pm.occupation,
            pm.annual_income,
            pm.residence_type,
            pm.distance_km
        FROM contract_party_codes cpc
        JOIN party_masters pm
          ON pm.party_code = cpc.party_code
        WHERE cpc.party_code IS NOT NULL
          AND TRIM(cpc.party_code) <> ''
        ORDER BY cpc.sort_order
        """;

    private static final RowMapper<PartyDraftDto> PARTY_DRAFT_ROW_MAPPER = (rs, rowNum) -> new PartyDraftDto(
        rs.getString("role"),
        rs.getString("party_code"),
        rs.getString("customer_type"),
        rs.getString("salutation"),
        rs.getString("full_name"),
        rs.getString("firm_name"),
        rs.getString("partner_1_name"),
        rs.getString("partner_2_name"),
        rs.getString("swd_name"),
        rs.getString("address_line_1"),
        rs.getString("address_line_2"),
        rs.getString("area"),
        rs.getString("city"),
        rs.getString("state"),
        rs.getString("pin_code"),
        rs.getString("contact_number"),
        rs.getString("alternative_number"),
        rs.getString("email_id"),
        rs.getObject("date_of_birth", java.time.LocalDate.class),
        rs.getString("pan_number"),
        rs.getString("aadhaar_number"),
        rs.getString("occupation"),
        rs.getBigDecimal("annual_income"),
        rs.getString("residence_type"),
        rs.getBigDecimal("distance_km")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public ContractPartyDraftRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Long nextPartyId() {
        Long id = namedParameterJdbcTemplate.getJdbcTemplate().queryForObject(NEXT_PARTY_ID_SQL, Long.class);
        if (id == null) {
            throw new IllegalStateException("Unable to allocate party id");
        }
        return id;
    }

    public Long nextContractId() {
        Long id = namedParameterJdbcTemplate.getJdbcTemplate().queryForObject(NEXT_CONTRACT_ID_SQL, Long.class);
        if (id == null) {
            throw new IllegalStateException("Unable to allocate contract id");
        }
        return id;
    }

    public Long nextContractAuditId() {
        Long id = namedParameterJdbcTemplate.getJdbcTemplate().queryForObject(NEXT_CONTRACT_AUDIT_ID_SQL, Long.class);
        if (id == null) {
            throw new IllegalStateException("Unable to allocate contract audit id");
        }
        return id;
    }

    public boolean exists(String partyCode) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
            EXISTS_SQL,
            new MapSqlParameterSource().addValue("partyCode", partyCode),
            Integer.class
        );
        return count != null && count > 0;
    }

    public void insertParty(Long id, String partyCode, String partyType, PartyDraftDto party, String updatedBy) {
        namedParameterJdbcTemplate.update(INSERT_SQL, params(id, partyCode, partyType, party, updatedBy));
    }

    public void updateParty(String partyCode, PartyDraftDto party, String updatedBy) {
        namedParameterJdbcTemplate.update(UPDATE_SQL, params(null, partyCode, null, party, updatedBy));
    }

    public void insertContractDraft(
        Long contractId,
        Long auditId,
        String contractNumber,
        String borrowerCode,
        String coApplicantCode,
        String guarantorCode,
        String guarantor2Code,
        String updatedBy
    ) {
        namedParameterJdbcTemplate.update(
            INSERT_CONTRACT_DRAFT_SQL,
            contractParams(contractId, auditId, contractNumber, borrowerCode, coApplicantCode, guarantorCode, guarantor2Code, updatedBy)
        );
    }

    public void updateContractDraft(
        Long contractId,
        String borrowerCode,
        String coApplicantCode,
        String guarantorCode,
        String guarantor2Code,
        String updatedBy
    ) {
        namedParameterJdbcTemplate.update(
            UPDATE_CONTRACT_DRAFT_SQL,
            contractParams(contractId, null, null, borrowerCode, coApplicantCode, guarantorCode, guarantor2Code, updatedBy)
        );
    }

    public String findContractNumber(Long contractId) {
        return namedParameterJdbcTemplate.queryForObject(
            FIND_CONTRACT_NUMBER_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId),
            String.class
        );
    }

    public List<PartyDraftDto> findContractParties(Long contractId) {
        return namedParameterJdbcTemplate.query(
            FIND_CONTRACT_PARTIES_SQL,
            new MapSqlParameterSource().addValue("contractId", contractId),
            PARTY_DRAFT_ROW_MAPPER
        );
    }

    private MapSqlParameterSource contractParams(
        Long contractId,
        Long auditId,
        String contractNumber,
        String borrowerCode,
        String coApplicantCode,
        String guarantorCode,
        String guarantor2Code,
        String updatedBy
    ) {
        return new MapSqlParameterSource()
            .addValue("contractId", contractId)
            .addValue("auditId", auditId)
            .addValue("contractNumber", contractNumber)
            .addValue("borrowerCode", borrowerCode)
            .addValue("coApplicantCode", coApplicantCode)
            .addValue("guarantorCode", guarantorCode)
            .addValue("guarantor2Code", guarantor2Code)
            .addValue("updatedBy", updatedBy);
    }

    private MapSqlParameterSource params(Long id, String partyCode, String partyType, PartyDraftDto party, String updatedBy) {
        return new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("partyCode", partyCode)
            .addValue("partyType", partyType)
            .addValue("customerType", customerType(party.customerType()))
            .addValue("salutation", clean(party.salutation()))
            .addValue("fullName", fullName(party))
            .addValue("firmName", clean(party.firmName()))
            .addValue("partner1Name", clean(party.partner1Name()))
            .addValue("partner2Name", clean(party.partner2Name()))
            .addValue("swdName", clean(party.swdName()))
            .addValue("addressLine1", clean(party.addressLine1()))
            .addValue("addressLine2", clean(party.addressLine2()))
            .addValue("area", clean(party.area()))
            .addValue("city", clean(party.city()))
            .addValue("state", clean(party.state()))
            .addValue("pinCode", clean(party.pinCode()))
            .addValue("contactNumber", clean(party.contactNumber()))
            .addValue("alternativeNumber", clean(party.alternativeNumber()))
            .addValue("emailId", clean(party.emailId()))
            .addValue("dateOfBirth", party.dateOfBirth())
            .addValue("panNumber", clean(party.panNumber()))
            .addValue("aadhaarNumber", clean(party.aadhaarNumber()))
            .addValue("occupation", clean(party.occupation()))
            .addValue("annualIncome", party.annualIncome())
            .addValue("residenceType", clean(party.residenceType()))
            .addValue("distanceKm", party.distanceKm())
            .addValue("updatedBy", updatedBy);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String fullName(PartyDraftDto party) {
        String fullName = clean(party.fullName());
        if (fullName != null) {
            return fullName;
        }
        String firmName = clean(party.firmName());
        return firmName == null ? "-" : firmName;
    }

    private String customerType(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return "Individual";
        }

        String normalized = cleaned.toLowerCase().replaceAll("[\\s_-]+", "");
        return switch (normalized) {
            case "firm" -> "Firm";
            case "partnership", "partner" -> "Partnership";
            case "pvtltd", "privatelimited", "pvtlimited" -> "Pvt Ltd";
            default -> "Individual";
        };
    }
}
