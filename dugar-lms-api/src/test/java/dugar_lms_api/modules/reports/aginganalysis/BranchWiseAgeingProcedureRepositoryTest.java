package dugar_lms_api.modules.reports.aginganalysis;

import dugar_lms_api.modules.reports.ReportAccessScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BranchWiseAgeingProcedureRepositoryTest {

    @Test
    void contractReportProcedureCallBindsAdminUserGroup() throws Exception {
        PreparedStatement call = mock(PreparedStatement.class);
        BranchWiseAgeingProcedureRepository repository = repository(call);
        LocalDate asOnDate = LocalDate.of(2026, 7, 31);

        assertThat(repository.getContractReportRows(asOnDate, "", new ReportAccessScope(false))).isEmpty();

        verify(call).setObject(1, asOnDate);
        verify(call).setString(2, null);
        verify(call).setString(3, "admin");
    }

    @Test
    void contractReportProcedureCallBindsUserUserGroup() throws Exception {
        PreparedStatement call = mock(PreparedStatement.class);
        BranchWiseAgeingProcedureRepository repository = repository(call);
        LocalDate asOnDate = LocalDate.of(2026, 7, 31);

        assertThat(repository.getContractReportRows(asOnDate, "F0001", new ReportAccessScope(true))).isEmpty();

        verify(call).setObject(1, asOnDate);
        verify(call).setString(2, "F0001");
        verify(call).setString(3, "user");
    }

    private BranchWiseAgeingProcedureRepository repository(PreparedStatement call) throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement select = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });
        when(connection.prepareStatement("CALL public.sp_branch_wise_ageing_test(?, ?, ?)")).thenReturn(call);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.contains("FROM tmp_contract_report r")))).thenReturn(select);
        when(select.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        return new BranchWiseAgeingProcedureRepository(jdbcTemplate);
    }
}
