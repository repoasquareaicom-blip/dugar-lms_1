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
import dugar_lms_api.modules.reports.ReportAccessScope;
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
        validate(request, null);
        return repository.save(request, userId);
    }

    public List<VoucherSummaryDto> search(String voucherType, String transactionType, String voucherNumber, LocalDate voucherDate, String contractNumber) {
        return search(voucherType, transactionType, voucherNumber, voucherDate, contractNumber, new ReportAccessScope(false));
    }

    public List<VoucherSummaryDto> search(String voucherType, String transactionType, String voucherNumber, LocalDate voucherDate, String contractNumber, ReportAccessScope accessScope) {
        return repository.search(voucherType, transactionType, voucherNumber, voucherDate, contractNumber, accessScope);
    }

    public PageResponse<VoucherSummaryDto> authorisationQueue(VoucherAuthorisationCriteria criteria) {
        return authorisationQueue(criteria, new ReportAccessScope(false));
    }

    public PageResponse<VoucherSummaryDto> authorisationQueue(VoucherAuthorisationCriteria criteria, ReportAccessScope accessScope) {
        return repository.authorisationQueue(criteria, accessScope);
    }

    public VoucherDto find(Long voucherHeaderId) {
        return find(voucherHeaderId, new ReportAccessScope(false));
    }

    public VoucherDto find(Long voucherHeaderId, ReportAccessScope accessScope) {
        return repository.find(voucherHeaderId, accessScope);
    }

    public VoucherReviewDto review(Long voucherHeaderId) {
        return repository.review(voucherHeaderId);
    }

    public VoucherReviewDto review(Long voucherHeaderId, ReportAccessScope accessScope) {
        return repository.review(voucherHeaderId, accessScope);
    }

    @Transactional
    public VoucherSaveResponse update(Long voucherHeaderId, VoucherSaveRequest request, Long userId) {
        return update(voucherHeaderId, request, userId, new ReportAccessScope(false));
    }

    @Transactional
    public VoucherSaveResponse update(Long voucherHeaderId, VoucherSaveRequest request, Long userId, ReportAccessScope accessScope) {
        validate(request, voucherHeaderId);
        return repository.update(voucherHeaderId, request, userId, accessScope);
    }

    @Transactional
    public VoucherDto authorise(Long voucherHeaderId, Long userId) {
        validateReview(repository.review(voucherHeaderId));
        return repository.authorise(voucherHeaderId, userId);
    }

    @Transactional
    public VoucherDto authorise(Long voucherHeaderId, Long userId, ReportAccessScope accessScope) {
        validateReview(repository.review(voucherHeaderId, accessScope));
        return repository.authorise(voucherHeaderId, userId, accessScope);
    }

    @Transactional
    public VoucherDto reject(Long voucherHeaderId, Long userId, String reason) {
        return reject(voucherHeaderId, userId, reason, new ReportAccessScope(false));
    }

    @Transactional
    public VoucherDto reject(Long voucherHeaderId, Long userId, String reason, ReportAccessScope accessScope) {
        requireReason(reason, "Rejection reason is mandatory.");
        return repository.reject(voucherHeaderId, userId, reason, accessScope);
    }

    @Transactional
    public VoucherDto cancel(Long voucherHeaderId, Long userId, String reason) {
        return cancel(voucherHeaderId, userId, reason, new ReportAccessScope(false));
    }

    @Transactional
    public VoucherDto cancel(Long voucherHeaderId, Long userId, String reason, ReportAccessScope accessScope) {
        requireReason(reason, "Cancellation reason is mandatory.");
        return repository.cancel(voucherHeaderId, userId, reason, accessScope);
    }

    @Transactional
    public VoucherDto reopen(Long voucherHeaderId, Long userId, String reason) {
        return reopen(voucherHeaderId, userId, reason, new ReportAccessScope(false));
    }

    @Transactional
    public VoucherDto reopen(Long voucherHeaderId, Long userId, String reason, ReportAccessScope accessScope) {
        return repository.reopen(voucherHeaderId, userId, reason, accessScope);
    }

    @Transactional
    public VoucherDto resubmit(Long voucherHeaderId, Long userId) {
        validateReview(repository.review(voucherHeaderId));
        return repository.resubmit(voucherHeaderId, userId);
    }

    @Transactional
    public VoucherDto resubmit(Long voucherHeaderId, Long userId, ReportAccessScope accessScope) {
        validateReview(repository.review(voucherHeaderId, accessScope));
        return repository.resubmit(voucherHeaderId, userId, accessScope);
    }

    private void validate(VoucherSaveRequest request, Long existingVoucherHeaderId) {
        String type = request.voucherType() == null ? "" : request.voucherType().trim().toUpperCase(Locale.ROOT);
        String nature = voucherNature(type);
        if (nature == null) {
            throw new IllegalArgumentException("Voucher type must be BP, BR, CP, CR, or JV.");
        }
        String voucherNumber = request.voucherNumber() == null ? "" : request.voucherNumber().trim();
        if (voucherNumber.isBlank()) {
            throw new IllegalArgumentException("Voucher No is required.");
        }
        if (!voucherNumber.matches("\\d+")) {
            throw new IllegalArgumentException("Voucher No must contain digits only.");
        }
        if (repository.voucherNumberExists(voucherNumber, existingVoucherHeaderId)) {
            throw new IllegalArgumentException("Voucher number " + voucherNumber + " already exists.");
        }
        if (request.voucherDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Voucher date cannot be a future date.");
        }
        if (amount(request.voucherAmount()).signum() <= 0) {
            throw new IllegalArgumentException("Voucher amount must be greater than zero.");
        }
        if (!"JOURNAL".equals(nature) && (request.headerControlCode() == null || request.headerControlCode().isBlank())) {
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
            if ("PAYMENT".equals(nature) && debit.signum() <= 0) {
                throw new IllegalArgumentException("Debit amount is required for payment voucher rows.");
            }
            if ("RECEIPT".equals(nature) && credit.signum() <= 0) {
                throw new IllegalArgumentException("Credit amount is required for receipt voucher rows.");
            }
            if ("JOURNAL".equals(nature) && debit.signum() <= 0 && credit.signum() <= 0) {
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

        if ("PAYMENT".equals(nature) && amount(request.voucherAmount()).compareTo(debitTotal) != 0) {
            throw new IllegalArgumentException("Debit total must match voucher amount.");
        }
        if ("RECEIPT".equals(nature) && amount(request.voucherAmount()).compareTo(creditTotal) != 0) {
            throw new IllegalArgumentException("Credit total must match voucher amount.");
        }
        if ("JOURNAL".equals(nature) && debitTotal.compareTo(creditTotal) != 0) {
            throw new IllegalArgumentException("Journal debit total must match credit total.");
        }
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateReview(VoucherReviewDto voucher) {
        String type = voucher.voucherType() == null ? "" : voucher.voucherType().trim().toUpperCase(Locale.ROOT);
        String nature = voucherNature(type);
        if (nature == null) {
            throw new IllegalArgumentException("Voucher type must be BP, BR, CP, CR, or JV.");
        }
        if (amount(voucher.voucherAmount()).signum() <= 0) {
            throw new IllegalArgumentException("Voucher amount must be greater than zero.");
        }
        if (!"JOURNAL".equals(nature) && (voucher.headerControlCode() == null || voucher.headerControlCode().isBlank())) {
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
            if ("PAYMENT".equals(nature) && debit.signum() <= 0) {
                throw new IllegalArgumentException("Debit amount is required for payment voucher rows.");
            }
            if ("RECEIPT".equals(nature) && credit.signum() <= 0) {
                throw new IllegalArgumentException("Credit amount is required for receipt voucher rows.");
            }
            if ("JOURNAL".equals(nature) && debit.signum() <= 0 && credit.signum() <= 0) {
                throw new IllegalArgumentException("Debit or credit amount is required for journal voucher rows.");
            }
        }
        if ("PAYMENT".equals(nature) && amount(voucher.voucherAmount()).compareTo(amount(voucher.totalDebit())) != 0) {
            throw new IllegalArgumentException("Debit total must match voucher amount.");
        }
        if ("RECEIPT".equals(nature) && amount(voucher.voucherAmount()).compareTo(amount(voucher.totalCredit())) != 0) {
            throw new IllegalArgumentException("Credit total must match voucher amount.");
        }
        if ("JOURNAL".equals(nature) && amount(voucher.totalDebit()).compareTo(amount(voucher.totalCredit())) != 0) {
            throw new IllegalArgumentException("Journal debit total must match credit total.");
        }
    }

    private void requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String voucherNature(String type) {
        return switch (type) {
            case "BP", "CP", "PAYMENT" -> "PAYMENT";
            case "BR", "CR", "RECEIPT" -> "RECEIPT";
            case "JV", "JOURNAL" -> "JOURNAL";
            default -> null;
        };
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value.trim();
    }
}
