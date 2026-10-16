package dugar_lms_api.modules.contracts.repository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class StockBankMarkingRepositoryTest {
 @Test void savesBankIdAsVarcharString() {
  var jdbc = mock(JdbcTemplate.class);
  var repository = new StockBankMarkingRepository(jdbc);
  when(jdbc.queryForList(contains("SELECT contract_detail_id"), eq(Long.class), eq(7L))).thenReturn(List.of(70L));
  repository.save(7L, 10L, "tester");
  verify(jdbc).update(contains("SET stock_marked_to_bank = ?"), eq("10"), eq("tester"), eq(70L));
 }
 @Test void searchRestrictsToActiveContracts() {
  var jdbc = mock(JdbcTemplate.class);
  new StockBankMarkingRepository(jdbc).byNumber("L-7");
  verify(jdbc).query(contains("WHERE c.is_active = TRUE AND UPPER(TRIM(c.contract_number))"),
   org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<dugar_lms_api.modules.contracts.dto.StockBankMarkingDto>>any(), eq("L-7"));
 }
 @Test void validatesExistenceAndActiveStatusWhilePreservingInactiveAssignment() {
  var jdbc = mock(JdbcTemplate.class);
  var repository = new StockBankMarkingRepository(jdbc);
  String sql = "SELECT is_active FROM bank_masters WHERE id = ? FOR SHARE";
  when(jdbc.queryForList(sql, Boolean.class, 1L)).thenReturn(List.of(true));
  when(jdbc.queryForList(sql, Boolean.class, 2L)).thenReturn(List.of(false));
  when(jdbc.queryForList(sql, Boolean.class, 3L)).thenReturn(List.of());
  repository.validateBank(1L, null);
  repository.validateBank(2L, 2L);
  repository.validateBank(null, 1L);
  assertThatIllegalArgumentException().isThrownBy(() -> repository.validateBank(2L, 1L));
  assertThatIllegalArgumentException().isThrownBy(() -> repository.validateBank(3L, 3L));
 }
}
