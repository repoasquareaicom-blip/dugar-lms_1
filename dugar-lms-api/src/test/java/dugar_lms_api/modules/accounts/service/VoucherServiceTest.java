package dugar_lms_api.modules.accounts.service;

import dugar_lms_api.modules.accounts.dto.VoucherDetailDto;
import dugar_lms_api.modules.accounts.dto.VoucherDetailRequest;
import dugar_lms_api.modules.accounts.dto.VoucherReviewDto;
import dugar_lms_api.modules.accounts.dto.VoucherSaveRequest;
import dugar_lms_api.modules.accounts.dto.VoucherSaveResponse;
import dugar_lms_api.modules.accounts.repository.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
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

    private VoucherSaveRequest request(String type, BigDecimal debit, BigDecimal credit) {
        return new VoucherSaveRequest(
            type,
            type,
            "AUTO",
            LocalDate.now(),
            LocalDate.now(),
            "CASH",
            new BigDecimal("100.00"),
            "GENERAL",
            null,
            null,
            null,
            null,
            "BANK",
            "Bank",
            null,
            false,
            List.of(new VoucherDetailRequest(1, "GENERAL", "EXP", "Expense", null, debit, credit, null, null, null, "Test", null))
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
            List.of(new VoucherDetailDto(1L, 1, "GENERAL", "LEDGER1", "Ledger", debitTotal, creditTotal, null, null, null, "Test", null)),
            List.of()
        );
    }
}
