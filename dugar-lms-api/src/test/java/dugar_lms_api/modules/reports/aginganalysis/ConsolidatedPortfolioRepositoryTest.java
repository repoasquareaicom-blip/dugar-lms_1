package dugar_lms_api.modules.reports.aginganalysis;

import dugar_lms_api.modules.reports.ReportAccessScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsolidatedPortfolioRepositoryTest {

    @Test
    void readsAllThreeTempTablesAfterProcedureOnSameConnection() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement call = mock(PreparedStatement.class);
        PreparedStatement tenorStatement = mock(PreparedStatement.class);
        PreparedStatement ticketStatement = mock(PreparedStatement.class);
        PreparedStatement stateStatement = mock(PreparedStatement.class);
        ResultSet tenor = mock(ResultSet.class);
        ResultSet ticket = mock(ResultSet.class);
        ResultSet state = mock(ResultSet.class);

        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });
        when(connection.prepareStatement("CALL public.sp_branch_wise_ageing_test(?, ?, ?)")).thenReturn(call);
        when(connection.prepareStatement("SELECT * FROM tmp_portfolio_tenor ORDER BY sort_order")).thenReturn(tenorStatement);
        when(connection.prepareStatement("SELECT * FROM tmp_portfolio_ticket_size ORDER BY sort_order")).thenReturn(ticketStatement);
        when(connection.prepareStatement("SELECT * FROM tmp_portfolio_state ORDER BY state_name")).thenReturn(stateStatement);
        when(tenorStatement.executeQuery()).thenReturn(tenor);
        when(ticketStatement.executeQuery()).thenReturn(ticket);
        when(stateStatement.executeQuery()).thenReturn(state);

        stubPortfolioRow(tenor, "tenor_band", "Up to 12 Months", new BigDecimal("8.35"));
        stubPortfolioRow(ticket, "ticket_size_band", "Below 1.00 Lakh", new BigDecimal("1.23"));
        stubStateRow(state);

        ConsolidatedPortfolioRepository repository = new ConsolidatedPortfolioRepository(jdbcTemplate);
        LocalDate asOnDate = LocalDate.of(2026, 8, 22);

        ConsolidatedPortfolioResponse response = repository.getPortfolio(asOnDate, "", new ReportAccessScope(false));

        assertThat(response.asOnDate()).isEqualTo(asOnDate);
        assertThat(response.areaCode()).isEqualTo("");
        assertThat(response.tenorWise()).hasSize(1);
        assertThat(response.ticketSizeWise()).hasSize(1);
        assertThat(response.stateWise()).hasSize(1);
        assertThat(response.tenorWise().get(0).principalOutstandingCr()).isEqualByComparingTo("8.35");
        assertThat(response.ticketSizeWise().get(0).principalOutstandingCr()).isEqualByComparingTo("1.23");
        assertThat(response.stateWise().get(0).principalOutstandingCr()).isEqualByComparingTo("255.10");

        verify(call).setObject(1, asOnDate);
        verify(call).setString(2, "");
        verify(call).setString(3, "admin");
        var inOrder = inOrder(connection);
        inOrder.verify(connection).prepareStatement("CALL public.sp_branch_wise_ageing_test(?, ?, ?)");
        inOrder.verify(connection).prepareStatement("SELECT * FROM tmp_portfolio_tenor ORDER BY sort_order");
        inOrder.verify(connection).prepareStatement("SELECT * FROM tmp_portfolio_ticket_size ORDER BY sort_order");
        inOrder.verify(connection).prepareStatement("SELECT * FROM tmp_portfolio_state ORDER BY state_name");
    }

    @Test
    void emptyResultSetsReturnEmptyLists() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement call = mock(PreparedStatement.class);
        PreparedStatement tenorStatement = mock(PreparedStatement.class);
        PreparedStatement ticketStatement = mock(PreparedStatement.class);
        PreparedStatement stateStatement = mock(PreparedStatement.class);
        ResultSet tenor = mock(ResultSet.class);
        ResultSet ticket = mock(ResultSet.class);
        ResultSet state = mock(ResultSet.class);

        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });
        when(connection.prepareStatement("CALL public.sp_branch_wise_ageing_test(?, ?, ?)")).thenReturn(call);
        when(connection.prepareStatement("SELECT * FROM tmp_portfolio_tenor ORDER BY sort_order")).thenReturn(tenorStatement);
        when(connection.prepareStatement("SELECT * FROM tmp_portfolio_ticket_size ORDER BY sort_order")).thenReturn(ticketStatement);
        when(connection.prepareStatement("SELECT * FROM tmp_portfolio_state ORDER BY state_name")).thenReturn(stateStatement);
        when(tenorStatement.executeQuery()).thenReturn(tenor);
        when(ticketStatement.executeQuery()).thenReturn(ticket);
        when(stateStatement.executeQuery()).thenReturn(state);
        when(tenor.next()).thenReturn(false);
        when(ticket.next()).thenReturn(false);
        when(state.next()).thenReturn(false);

        ConsolidatedPortfolioResponse response = new ConsolidatedPortfolioRepository(jdbcTemplate)
            .getPortfolio(LocalDate.of(2026, 8, 22), "F0001", new ReportAccessScope(true));

        assertThat(response.tenorWise()).isEmpty();
        assertThat(response.ticketSizeWise()).isEmpty();
        assertThat(response.stateWise()).isEmpty();
        verify(call).setString(2, "F0001");
        verify(call).setString(3, "user");
    }

    private void stubPortfolioRow(ResultSet rs, String labelColumn, String label, BigDecimal principalOutstanding) throws Exception {
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt("sort_order")).thenReturn(1);
        when(rs.getString(labelColumn)).thenReturn(label);
        when(rs.getLong("no_of_accounts")).thenReturn(221L);
        stubMoney(rs, principalOutstanding);
    }

    private void stubStateRow(ResultSet rs) throws Exception {
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("state_code")).thenReturn("TN");
        when(rs.getString("state_name")).thenReturn("Tamil Nadu");
        when(rs.getLong("no_of_accounts")).thenReturn(5242L);
        stubMoney(rs, new BigDecimal("255.10"));
    }

    private void stubMoney(ResultSet rs, BigDecimal principalOutstanding) throws Exception {
        when(rs.getBigDecimal("principal_outstanding_cr")).thenReturn(principalOutstanding);
        when(rs.getBigDecimal("standard_cr")).thenReturn(new BigDecimal("200.01"));
        when(rs.getBigDecimal("days_0_30_cr")).thenReturn(new BigDecimal("10.02"));
        when(rs.getBigDecimal("days_31_60_cr")).thenReturn(new BigDecimal("9.03"));
        when(rs.getBigDecimal("days_61_90_cr")).thenReturn(new BigDecimal("8.04"));
        when(rs.getBigDecimal("days_91_180_cr")).thenReturn(new BigDecimal("7.05"));
        when(rs.getBigDecimal("days_181_365_cr")).thenReturn(new BigDecimal("6.06"));
        when(rs.getBigDecimal("days_above_365_cr")).thenReturn(new BigDecimal("5.07"));
    }
}
