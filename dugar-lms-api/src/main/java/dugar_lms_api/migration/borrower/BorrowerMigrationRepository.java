package dugar_lms_api.migration.borrower;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class BorrowerMigrationRepository {

    private static final String TABLE_COLUMNS_SQL = """
        SELECT column_name
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = :tableName
        """;

    private static final String FIND_PARTY_SQL = """
        SELECT
            party_code,
            party_type,
            salutation,
            full_name,
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
            firm_name,
            is_active
        FROM party_masters
        WHERE party_code = :partyCode
        """;

    private static final String INSERT_PARTY_SQL = """
        INSERT INTO party_masters (
            party_code,
            party_type,
            salutation,
            full_name,
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
            firm_name,
            created_by,
            is_active
        )
        VALUES (
            :partyCode,
            :partyType,
            :salutation,
            :fullName,
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
            :firmName,
            :createdBy,
            :isActive
        )
        """;

    private static final String DISTINCT_BORROWER_CODES_SQL = """
        SELECT DISTINCT UPPER(TRIM(borrower_code))
        FROM contracts
        WHERE borrower_code IS NOT NULL
          AND TRIM(borrower_code) <> ''
        """;

    private static final String DISTINCT_GUARANTOR_CODES_SQL = """
        SELECT DISTINCT UPPER(TRIM(guarantor_code))
        FROM contracts
        WHERE guarantor_code IS NOT NULL
          AND TRIM(guarantor_code) <> ''
        """;

    private static final String BLANK_BORROWER_CODES_SQL = """
        SELECT COUNT(*)
        FROM contracts
        WHERE borrower_code IS NULL
           OR TRIM(borrower_code) = ''
        """;

    private static final String BLANK_GUARANTOR_CODES_SQL = """
        SELECT COUNT(*)
        FROM contracts
        WHERE guarantor_code IS NULL
           OR TRIM(guarantor_code) = ''
        """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public BorrowerMigrationRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Set<String> tableColumns(String tableName) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tableName", tableName);
        return new LinkedHashSet<>(namedParameterJdbcTemplate.queryForList(TABLE_COLUMNS_SQL, params, String.class));
    }

    public Optional<PartyMasterRecord> findByPartyCode(String partyCode) {
        try {
            return Optional.ofNullable(namedParameterJdbcTemplate.queryForObject(
                FIND_PARTY_SQL,
                new MapSqlParameterSource().addValue("partyCode", partyCode),
                partyRowMapper()
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void insertParty(PartyMasterRecord party, String createdBy) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("partyCode", party.partyCode())
            .addValue("partyType", party.partyType().name())
            .addValue("salutation", party.salutation())
            .addValue("fullName", party.fullName())
            .addValue("swdName", party.swdName())
            .addValue("addressLine1", party.addressLine1())
            .addValue("addressLine2", party.addressLine2())
            .addValue("area", party.area())
            .addValue("city", party.city())
            .addValue("state", party.state())
            .addValue("pinCode", party.pinCode())
            .addValue("contactNumber", party.contactNumber())
            .addValue("alternativeNumber", party.alternativeNumber())
            .addValue("emailId", party.emailId())
            .addValue("dateOfBirth", party.dateOfBirth())
            .addValue("panNumber", party.panNumber())
            .addValue("aadhaarNumber", party.aadhaarNumber())
            .addValue("occupation", party.occupation())
            .addValue("annualIncome", party.annualIncome())
            .addValue("firmName", party.firmName())
            .addValue("createdBy", createdBy)
            .addValue("isActive", party.isActive());

        namedParameterJdbcTemplate.update(INSERT_PARTY_SQL, params);
    }

    public Set<String> distinctContractBorrowerCodes() {
        return new LinkedHashSet<>(namedParameterJdbcTemplate.queryForList(
            DISTINCT_BORROWER_CODES_SQL,
            new MapSqlParameterSource(),
            String.class
        ));
    }

    public Set<String> distinctContractGuarantorCodes() {
        return new LinkedHashSet<>(namedParameterJdbcTemplate.queryForList(
            DISTINCT_GUARANTOR_CODES_SQL,
            new MapSqlParameterSource(),
            String.class
        ));
    }

    public int blankContractBorrowerCodeCount() {
        Integer count = namedParameterJdbcTemplate.queryForObject(
            BLANK_BORROWER_CODES_SQL,
            new MapSqlParameterSource(),
            Integer.class
        );
        return count == null ? 0 : count;
    }

    public int blankContractGuarantorCodeCount() {
        Integer count = namedParameterJdbcTemplate.queryForObject(
            BLANK_GUARANTOR_CODES_SQL,
            new MapSqlParameterSource(),
            Integer.class
        );
        return count == null ? 0 : count;
    }

    public Set<String> existingPartyCodes(Set<String> partyCodes, PartyType partyType) {
        if (partyCodes.isEmpty()) {
            return Set.of();
        }

        String sql = """
            SELECT UPPER(TRIM(party_code))
            FROM party_masters
            WHERE UPPER(TRIM(party_code)) IN (:partyCodes)
              AND party_type = :partyType
            """;

        return new LinkedHashSet<>(namedParameterJdbcTemplate.queryForList(
            sql,
            new MapSqlParameterSource()
                .addValue("partyCodes", partyCodes)
                .addValue("partyType", partyType.name()),
            String.class
        ));
    }

    private RowMapper<PartyMasterRecord> partyRowMapper() {
        return (rs, rowNum) -> new PartyMasterRecord(
            rs.getString("party_code"),
            PartyType.valueOf(rs.getString("party_type")),
            rs.getString("salutation"),
            rs.getString("full_name"),
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
            rs.getString("firm_name"),
            rs.getBoolean("is_active")
        );
    }
}
