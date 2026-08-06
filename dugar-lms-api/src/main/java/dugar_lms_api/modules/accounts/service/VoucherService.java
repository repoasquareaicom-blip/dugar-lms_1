package dugar_lms_api.modules.accounts.service;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accounts.dto.VoucherAuthorisationCriteria;
import dugar_lms_api.modules.accounts.dto.VoucherDetailRequest;
import dugar_lms_api.modules.accounts.dto.VoucherDetailDto;
import dugar_lms_api.modules.accounts.dto.VoucherDto;
import dugar_lms_api.modules.accounts.dto.VoucherReviewDto;
import dugar_lms_api.modules.accounts.dto.VoucherSaveRequest;
import dugar_lms_api.modules.accounts.dto.VoucherSaveResponse;
import dugar_lms_api.modules.accounts.dto.VoucherSummaryDto;
import dugar_lms_api.modules.accounts.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.List;

@Service
public class VoucherService {

    private final VoucherRepository repository;

    public VoucherService(VoucherRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public VoucherSaveResponse save(VoucherSaveRequest request, Long userId) {
        validate(request);
        return repository.save(request, userId);
    }

    public List<VoucherSummaryDto> search(String voucherType, String transactionType, String voucherNumber, LocalDate voucherDate, String contractNumber) {
        return repository.search(voucherType, transactionType, voucherNumber, voucherDate, contractNumber);
    }

    public PageResponse<VoucherSummaryDto> authorisationQueue(VoucherAuthorisationCriteria criteria) {
        return repository.authorisationQueue(criteria);
    }

    public VoucherDto find(Long voucherHeaderId) {
        return repository.find(voucherHeaderId);
    }

    public VoucherReviewDto review(Long voucherHeaderId) {
        return repository.review(voucherHeaderId);
    }

    @Transactional
    public VoucherSaveResponse update(Long voucherHeaderId, VoucherSaveRequest request, Long userId) {
        validate(request);
        return repository.update(voucherHeaderId, request, userId);
    }

    @Transactional
    public VoucherDto authorise(Long voucherHeaderId, Long userId) {
        validateReview(repository.review(voucherHeaderId));
        return repository.authorise(voucherHeaderId, userId);
    }

    @Transactional
    public VoucherDto reject(Long voucherHeaderId, Long userId, String reason) {
        requireReason(reason, "Rejection reason is mandatory.");
        return repository.reject(voucherHeaderId, userId, reason);
    }

    @Transactional
    public VoucherDto cancel(Long voucherHeaderId, Long userId, String reason) {
        requireReason(reason, "Cancellation reason is mandatory.");
        return repository.cancel(voucherHeaderId, userId, reason);
    }

    @Transactional
    public VoucherDto reopen(Long voucherHeaderId, Long userId, String reason) {
        return repository.reopen(voucherHeaderId, userId, reason);
    }

    @Transactional
    public VoucherDto resubmit(Long voucherHeaderId, Long userId) {
        validateReview(repository.review(voucherHeaderId));
        return repository.resubmit(voucherHeaderId, userId);
    }

    private void validate(VoucherSaveRequest request) {
        String type = request.voucherType() == null ? "" : request.voucherType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PAYMENT", "RECEIPT", "JOURNAL").contains(type)) {
            throw new IllegalArgumentException("Voucher type must be Payment, Receipt, or Journal.");
        }
        if (request.voucherDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Voucher date cannot be a future date.");
        }
        if (amount(request.voucherAmount()).signum() <= 0) {
            throw new IllegalArgumentException("Voucher amount must be greater than zero.");
        }
        if (!"JOURNAL".equals(type) && (request.headerControlCode() == null || request.headerControlCode().isBlank())) {
            throw new IllegalArgumentException("Header control code is required.");
        }
        if (request.headerControlCode() != null && !request.headerControlCode().isBlank() && !repository.activeLedgerExists(request.headerControlCode())) {
            throw new IllegalArgumentException("Header control code " + request.headerControlCode() + " is inactive or was not found.");
        }

        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        Set<String> duplicateKeys = new HashSet<>();

        for (VoucherDetailRequest detail : request.details()) {
            BigDecimal debit = amount(detail.debitAmount());
            BigDecimal credit = amount(detail.creditAmount());
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new IllegalArgumentException("Debit and credit amounts cannot be negative.");
            }
            if ("PAYMENT".equals(type) && debit.signum() <= 0) {
                throw new IllegalArgumentException("Debit amount is required for payment voucher rows.");
            }
            if ("RECEIPT".equals(type) && credit.signum() <= 0) {
                throw new IllegalArgumentException("Credit amount is required for receipt voucher rows.");
            }
            if ("JOURNAL".equals(type) && debit.signum() <= 0 && credit.signum() <= 0) {
                throw new IllegalArgumentException("Debit or credit amount is required for journal voucher rows.");
            }
            if (!repository.activeLedgerExists(detail.ledgerCode())) {
                throw new IllegalArgumentException("Details code " + detail.ledgerCode() + " is inactive or was not found.");
            }
            if (detail.loanReference() != null && !detail.loanReference().isBlank() && !repository.activeContractExists(detail.loanReference())) {
                throw new IllegalArgumentException("Loan reference " + detail.loanReference() + " was not found or is not active.");
            }

            String duplicateKey = (detail.ledgerCode() + "|"
                + nullToBlank(detail.loanReference()) + "|"
                + debit.stripTrailingZeros().toPlainString() + "|"
                + credit.stripTrailingZeros().toPlainString()).toUpperCase(Locale.ROOT);
            if (!Boolean.TRUE.equals(request.allowDuplicateDetails()) && !duplicateKeys.add(duplicateKey)) {
                throw new IllegalArgumentException("Duplicate voucher detail row found. Please review before saving.");
            }

            debitTotal = debitTotal.add(debit);
            creditTotal = creditTotal.add(credit);
        }

        if ("PAYMENT".equals(type) && amount(request.voucherAmount()).compareTo(debitTotal) != 0) {
            throw new IllegalArgumentException("Debit total must match voucher amount.");
        }
        if ("RECEIPT".equals(type) && amount(request.voucherAmount()).compareTo(creditTotal) != 0) {
            throw new IllegalArgumentException("Credit total must match voucher amount.");
        }
        if ("JOURNAL".equals(type) && debitTotal.compareTo(creditTotal) != 0) {
            throw new IllegalArgumentException("Journal debit total must match credit total.");
        }
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateReview(VoucherReviewDto voucher) {
        String type = voucher.voucherType() == null ? "" : voucher.voucherType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PAYMENT", "RECEIPT", "JOURNAL").contains(type)) {
            throw new IllegalArgumentException("Voucher type must be Payment, Receipt, or Journal.");
        }
        if (amount(voucher.voucherAmount()).signum() <= 0) {
            throw new IllegalArgumentException("Voucher amount must be greater than zero.");
        }
        if (!"JOURNAL".equals(type) && (voucher.headerControlCode() == null || voucher.headerControlCode().isBlank())) {
            throw new IllegalArgumentException("Header control code is required.");
        }
        if (voucher.details() == null || voucher.details().isEmpty()) {
            throw new IllegalArgumentException("At least one detail row is required.");
        }
        for (VoucherDetailDto detail : voucher.details()) {
            BigDecimal debit = amount(detail.debitAmount());
            BigDecimal credit = amount(detail.creditAmount());
            if (!repository.activeLedgerExists(detail.ledgerCode())) {
                throw new IllegalArgumentException("Details code " + detail.ledgerCode() + " is inactive or was not found.");
            }
            if ("PAYMENT".equals(type) && debit.signum() <= 0) {
                throw new IllegalArgumentException("Debit amount is required for payment voucher rows.");
            }
            if ("RECEIPT".equals(type) && credit.signum() <= 0) {
                throw new IllegalArgumentException("Credit amount is required for receipt voucher rows.");
            }
            if ("JOURNAL".equals(type) && debit.signum() <= 0 && credit.signum() <= 0) {
                throw new IllegalArgumentException("Debit or credit amount is required for journal voucher rows.");
            }
        }
        if ("PAYMENT".equals(type) && amount(voucher.voucherAmount()).compareTo(amount(voucher.totalDebit())) != 0) {
            throw new IllegalArgumentException("Debit total must match voucher amount.");
        }
        if ("RECEIPT".equals(type) && amount(voucher.voucherAmount()).compareTo(amount(voucher.totalCredit())) != 0) {
            throw new IllegalArgumentException("Credit total must match voucher amount.");
        }
        if ("JOURNAL".equals(type) && amount(voucher.totalDebit()).compareTo(amount(voucher.totalCredit())) != 0) {
            throw new IllegalArgumentException("Journal debit total must match credit total.");
        }
    }

    private void requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value.trim();
    }
}
