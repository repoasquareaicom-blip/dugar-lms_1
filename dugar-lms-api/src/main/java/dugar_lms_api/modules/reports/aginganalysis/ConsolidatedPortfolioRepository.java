package dugar_lms_api.modules.reports.aginganalysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ConsolidatedPortfolioRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsolidatedPortfolioRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public ConsolidatedPortfolioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ConsolidatedPortfolioResponse getPortfolio(LocalDate asOnDate, String areaCode) {
        String normalizedArea = clean(areaCode);
        long startedAt = System.nanoTime();
        ConsolidatedPortfolioResponse response = jdbcTemplate.execute((ConnectionCallback<ConsolidatedPortfolioResponse>) connection -> {
            callProcedure(connection, asOnDate, normalizedArea);
            return new ConsolidatedPortfolioResponse(
                asOnDate,
                normalizedArea,
                readPortfolioRows(connection, "SELECT * FROM tmp_portfolio_tenor ORDER BY sort_order", "tenor_band"),
                readPortfolioRows(connection, "SELECT * FROM tmp_portfolio_ticket_size ORDER BY sort_order", "ticket_size_band"),
                readStateRows(connection)
            );
        });
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        LOGGER.info("Consolidated Portfolio procedure completed in {} ms for date {} and area {}", elapsedMs, asOnDate, normalizedArea);
        return response == null
            ? new ConsolidatedPortfolioResponse(asOnDate, normalizedArea, List.of(), List.of(), List.of())
            : response;
    }

    private void callProcedure(Connection connection, LocalDate asOnDate, String areaCode) throws SQLException {
        try (var call = connection.prepareStatement("CALL public.sp_branch_wise_ageing_test(?, ?)")) {
            call.setObject(1, asOnDate);
            call.setString(2, areaCode);
            call.execute();
        }
    }

    private List<ConsolidatedPortfolioRowDto> readPortfolioRows(Connection connection, String sql, String labelColumn) throws SQLException {
        try (var statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<ConsolidatedPortfolioRowDto> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new ConsolidatedPortfolioRowDto(
                    rs.getInt("sort_order"),
                    rs.getString(labelColumn),
                    rs.getLong("no_of_accounts"),
                    money(rs.getBigDecimal("principal_outstanding_cr")),
                    money(rs.getBigDecimal("standard_cr")),
                    money(rs.getBigDecimal("days_0_30_cr")),
                    money(rs.getBigDecimal("days_31_60_cr")),
                    money(rs.getBigDecimal("days_61_90_cr")),
                    money(rs.getBigDecimal("days_91_180_cr")),
                    money(rs.getBigDecimal("days_181_365_cr")),
                    money(rs.getBigDecimal("days_above_365_cr"))
                ));
            }
            return rows;
        }
    }

    private List<ConsolidatedPortfolioStateRowDto> readStateRows(Connection connection) throws SQLException {
        String sql = "SELECT * FROM tmp_portfolio_state ORDER BY state_name";
        try (var statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<ConsolidatedPortfolioStateRowDto> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new ConsolidatedPortfolioStateRowDto(
                    rs.getString("state_code"),
                    rs.getString("state_name"),
                    rs.getLong("no_of_accounts"),
                    money(rs.getBigDecimal("principal_outstanding_cr")),
                    money(rs.getBigDecimal("standard_cr")),
                    money(rs.getBigDecimal("days_0_30_cr")),
                    money(rs.getBigDecimal("days_31_60_cr")),
                    money(rs.getBigDecimal("days_61_90_cr")),
                    money(rs.getBigDecimal("days_91_180_cr")),
                    money(rs.getBigDecimal("days_181_365_cr")),
                    money(rs.getBigDecimal("days_above_365_cr"))
                ));
            }
            return rows;
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
