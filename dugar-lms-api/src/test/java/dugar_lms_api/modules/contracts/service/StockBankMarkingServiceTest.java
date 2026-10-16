package dugar_lms_api.modules.contracts.service;
import dugar_lms_api.modules.contracts.dto.StockBankMarkingDto;
import dugar_lms_api.modules.contracts.repository.StockBankMarkingRepository;
import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import dugar_lms_api.modules.contracts.controller.StockBankMarkingController;
import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class StockBankMarkingServiceTest {
 private final StockBankMarkingRepository repository = mock(StockBankMarkingRepository.class);
 private final ContractAccessRepository access = mock(ContractAccessRepository.class);
 private final StockBankMarkingService service = new StockBankMarkingService(repository, access);
 private final TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "password");
 private StockBankMarkingDto contract(Long bank) {
  return new StockBankMarkingDto(7L, "L-7", null, "Borrower", null, "Make", "Reg", "L", bank, "Bank");
 }
 @Test void searchesContractAndEnforcesScope() {
  when(repository.byNumber("L-7")).thenReturn(List.of(contract(1L)));
  assertThat(service.search(" L-7 ", auth).borrowerName()).isEqualTo("Borrower");
  verify(access).requireAccess(eq(7L), any());
  when(repository.byNumber("L-7")).thenReturn(List.of(contract(1L), contract(2L)));
  assertThatIllegalArgumentException().isThrownBy(() -> service.search("L-7", auth));
 }
 @Test void savesAssignmentWithAuditAndReturnsRefreshedBank() {
  auth.setDetails(Map.of("userId", 12L, "userGroup", "admin"));
  when(repository.byId(7L)).thenReturn(contract(1L), contract(2L));
  assertThat(service.save(7L, 2L, auth).bankId()).isEqualTo(2L);
  var order = inOrder(access, repository);
  order.verify(access).requireAccess(eq(7L), any());
  order.verify(repository).lockActiveContract(7L);
  order.verify(repository).lockContract(7L);
  order.verify(repository).byId(7L);
  order.verify(repository).validateBank(2L, 1L);
  order.verify(repository).save(7L, 2L, "12");
 }
 @Test void invalidBankAndMissingSelectionNeverSave() {
  when(repository.byId(7L)).thenReturn(contract(1L));
  doThrow(new IllegalArgumentException("inactive")).when(repository).validateBank(2L, 1L);
  assertThatIllegalArgumentException().isThrownBy(() -> service.save(7L, 2L, auth));
  assertThatIllegalArgumentException().isThrownBy(() -> service.save(7L, null, auth));
  verify(repository, never()).save(any(), any(), any());
 }
 @Test void inactiveContractCannotBeMarked() {
  doThrow(new IllegalArgumentException("Active contract not found.")).when(repository).lockActiveContract(7L);
  assertThatIllegalArgumentException().isThrownBy(() -> service.save(7L, 1L, auth));
  verify(repository, never()).save(any(), any(), any());
 }
 @Test void unauthorizedMenuCannotReadOrWrite() {
  var menu = mock(MenuAccessService.class);
  var controller = new StockBankMarkingController(service, menu);
  assertThatThrownBy(() -> controller.search("L-7", auth)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(() -> controller.save(7L, new StockBankMarkingController.Request(1L), auth)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  verifyNoInteractions(repository, access);
 }
}
