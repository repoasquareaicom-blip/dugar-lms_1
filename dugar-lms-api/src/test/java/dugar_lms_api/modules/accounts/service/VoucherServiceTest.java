package dugar_lms_api.modules.accounts.service;

import dugar_lms_api.modules.accounts.dto.VoucherDetailDto;
import dugar_lms_api.modules.accounts.dto.VoucherDetailRequest;
import dugar_lms_api.modules.accounts.dto.VoucherEditRequest;
import dugar_lms_api.modules.accounts.dto.VoucherReviewDto;
import dugar_lms_api.modules.accounts.dto.VoucherSaveRequest;
import dugar_lms_api.modules.accounts.dto.VoucherSaveResponse;
import dugar_lms_api.modules.accounts.repository.VoucherRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository repository;

    @InjectMocks
    private VoucherService service;

    @Test
    void newPaymentVoucherUsesDebitTotalForSubmissionValidation() {
        VoucherSaveRequest request = request("PAYMENT", new BigDecimal("100.00"), new BigDecimal("0.00"));
        when(repository.activeLedgerExists("BANK")).thenReturn(true);
        when(repository.activeLedgerExists("EXP")).thenReturn(true);
        when(repository.save(request, 7L)).thenReturn(new VoucherSaveResponse(1L, "PAYMENT", "PV-1", request.voucherDate(), request.voucherAmount(), 1));

        service.save(request, 7L);

        verify(repository).save(request, 7L);
    }

    @Test
    void receiptVoucherRequiresCreditRows() {
        VoucherSaveRequest request = request("RECEIPT", new BigDecimal("100.00"), BigDecimal.ZERO);
        when(repository.activeLedgerExists("BANK")).thenReturn(true);

        assertThatThrownBy(() -> service.save(request, 7L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Credit amount is required");
    }

    @Test
    void saveRequiresNumericVoucherNumber() {
        VoucherSaveRequest request = request("PAYMENT", "CP-100", new BigDecimal("100.00"), BigDecimal.ZERO);

        assertThatThrownBy(() -> service.save(request, 7L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Voucher No must contain digits only");
    }

    @Test
    void saveRejectsDuplicateVoucherNumber() {
        VoucherSaveRequest request = request("PAYMENT", "123456", new BigDecimal("100.00"), BigDecimal.ZERO);
        when(repository.voucherNumberExists("123456", null)).thenReturn(true);

        assertThatThrownBy(() -> service.save(request, 7L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Voucher number 123456 already exists.");
    }

    @Test
    void rejectRequiresReason() {
        assertThatThrownBy(() -> service.reject(10L, 7L, " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Rejection reason is mandatory");
    }

    @Test
    void cancelRequiresReason() {
        assertThatThrownBy(() -> service.cancel(10L, 7L, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cancellation reason is mandatory");
    }

    @Test
    void unbalancedSubmittedVoucherCannotBeAuthorised() {
        VoucherReviewDto review = review("JOURNAL", new BigDecimal("100.00"), new BigDecimal("50.00"));
        when(repository.review(10L)).thenReturn(review);
        when(repository.activeLedgerExists("LEDGER1")).thenReturn(true);

        assertThatThrownBy(() -> service.authorise(10L, 7L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Journal debit total must match credit total");
    }

    @Test
    void reopenedVoucherCanBeResubmittedAfterValidation() {
        VoucherReviewDto review = review("JOURNAL", new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(repository.review(10L)).thenReturn(review);
        when(repository.activeLedgerExists("LEDGER1")).thenReturn(true);

        service.resubmit(10L, 7L);

        verify(repository).resubmit(10L, 7L);
    }

    @Test
    void loanCategoryWithMissingContractCannotSaveAuthorisedEdit() {
        VoucherSaveRequest request = request("BR", "LOAN", null, BigDecimal.ZERO, new BigDecimal("100.00"));
        when(repository.activeLedgerExists("BANK")).thenReturn(true);
        when(repository.activeLedgerExists("EXP")).thenReturn(true);

        assertThatThrownBy(() -> service.editAuthorised(10L, new VoucherEditRequest("Fix contract", request), 7L, new ReportAccessScope(false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Contract number is required for loan category vouchers.");

        verify(repository, never()).editAuthorised(any(), any(), any(), any(), any());
    }

    @Test
    void loanCategoryWithSelectedActiveContractCanSaveAuthorisedEdit() {
        VoucherSaveRequest request = request("BR", "LOAN", "CN-1", BigDecimal.ZERO, new BigDecimal("100.00"));
        when(repository.activeLedgerExists("BANK")).thenReturn(true);
        when(repository.activeLedgerExists("EXP")).thenReturn(true);
        when(repository.openActiveContractExists("CN-1")).thenReturn(true);
        when(repository.editAuthorised(10L, request, 7L, new ReportAccessScope(false), "Fix contract"))
            .thenReturn(new VoucherSaveResponse(10L, "BR", "123456", request.voucherDate(), request.voucherAmount(), 1));

        service.editAuthorised(10L, new VoucherEditRequest("Fix contract", request), 7L, new ReportAccessScope(false));

        verify(repository).editAuthorised(10L, request, 7L, new ReportAccessScope(false), "Fix contract");
    }

    @Test
    void loanCategoryWithInactiveOrClosedContractCannotSaveAuthorisedEdit() {
        VoucherSaveRequest request = request("BR", "LOAN", "CN-1", BigDecimal.ZERO, new BigDecimal("100.00"));
        when(repository.activeLedgerExists("BANK")).thenReturn(true);
        when(repository.activeLedgerExists("EXP")).thenReturn(true);
        when(repository.openActiveContractExists("CN-1")).thenReturn(false);

        assertThatThrownBy(() -> service.editAuthorised(10L, new VoucherEditRequest("Fix contract", request), 7L, new ReportAccessScope(false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Contract number CN-1 is closed, inactive, or was not found.");

        verify(repository, never()).editAuthorised(any(), any(), any(), any(), any());
    }

    @Test
    void nonLoanCategoryWithBlankContractCanSaveAuthorisedEdit() {
        VoucherSaveRequest request = request("BR", "GENERAL", null, BigDecimal.ZERO, new BigDecimal("100.00"));
        when(repository.activeLedgerExists("BANK")).thenReturn(true);
        when(repository.activeLedgerExists("EXP")).thenReturn(true);
        when(repository.editAuthorised(10L, request, 7L, new ReportAccessScope(false), "Fix general"))
            .thenReturn(new VoucherSaveResponse(10L, "BR", "123456", request.voucherDate(), request.voucherAmount(), 1));

        service.editAuthorised(10L, new VoucherEditRequest("Fix general", request), 7L, new ReportAccessScope(false));

        verify(repository).editAuthorised(10L, request, 7L, new ReportAccessScope(false), "Fix general");
        verify(repository, never()).openActiveContractExists(any());
    }

    private VoucherSaveRequest request(String type, BigDecimal debit, BigDecimal credit) {
        return request(type, "123456", debit, credit);
    }

    private VoucherSaveRequest request(String type, String voucherNumber, BigDecimal debit, BigDecimal credit) {
        return request(type, voucherNumber, "GENERAL", null, debit, credit);
    }

    private VoucherSaveRequest request(String type, String category, String contractNumber, BigDecimal debit, BigDecimal credit) {
        return request(type, "123456", category, contractNumber, debit, credit);
    }

    private VoucherSaveRequest request(String type, String voucherNumber, String category, String contractNumber, BigDecimal debit, BigDecimal credit) {
        return new VoucherSaveRequest(
            type,
            type,
            voucherNumber,
            LocalDate.now(),
            LocalDate.now(),
            "CASH",
            new BigDecimal("100.00"),
            category,
            null,
            contractNumber,
            null,
            null,
            "BANK",
            "Bank",
            null,
            false,
            List.of(new VoucherDetailRequest(1, category, "EXP", "Expense", null, debit, credit, null, null, null, "Test", null))
        );
    }

    private VoucherReviewDto review(String type, BigDecimal debitTotal, BigDecimal creditTotal) {
        return new VoucherReviewDto(
            10L,
            type,
            type,
            "V-10",
            LocalDate.now(),
            LocalDate.now(),
            "CASH",
            new BigDecimal("100.00"),
            null,
            null,
            "BANK",
            "Bank",
            null,
            "SUBMITTED",
            1,
            1L,
            2L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            debitTotal,
            creditTotal,
            List.of(new VoucherDetailDto(1L, 1, "GENERAL", "LEDGER1", "Ledger", null, debitTotal, creditTotal, null, null, null, "Test", null)),
            List.of()
        );
    }
}
