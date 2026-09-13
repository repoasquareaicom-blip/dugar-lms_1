package dugar_lms_api.modules.accounts.controller;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accounts.dto.VoucherActionRequest;
import dugar_lms_api.modules.accounts.dto.VoucherAuthorisationCriteria;
import dugar_lms_api.modules.accounts.dto.VoucherDto;
import dugar_lms_api.modules.accounts.dto.VoucherReviewDto;
import dugar_lms_api.modules.accounts.dto.VoucherSaveRequest;
import dugar_lms_api.modules.accounts.dto.VoucherSaveResponse;
import dugar_lms_api.modules.accounts.dto.VoucherSummaryDto;
import dugar_lms_api.modules.accounts.service.VoucherService;
import dugar_lms_api.modules.reports.ReportAccessScope;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts/vouchers")
public class VoucherController {

    private final VoucherService service;

    public VoucherController(VoucherService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<VoucherSaveResponse> save(@Valid @RequestBody VoucherSaveRequest request, Authentication authentication) {
        return ResponseEntity.ok(service.save(request, userId(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<VoucherSummaryDto>> search(
        @RequestParam(required = false) String voucherType,
        @RequestParam(required = false) String transactionType,
        @RequestParam(required = false) String voucherNumber,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate voucherDate,
        @RequestParam(required = false) String contractNumber,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.search(voucherType, transactionType, voucherNumber, voucherDate, contractNumber, ReportAccessScope.from(authentication)));
    }

    @GetMapping("/authorisation-queue")
    public ResponseEntity<PageResponse<VoucherSummaryDto>> authorisationQueue(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String voucherType,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate voucherDateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate voucherDateTo,
        @RequestParam(required = false) BigDecimal minimumAmount,
        @RequestParam(required = false) BigDecimal maximumAmount,
        @RequestParam(required = false) Long submittedBy,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sortColumn,
        @RequestParam(required = false) String sortDirection,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.authorisationQueue(new VoucherAuthorisationCriteria(
            keyword,
            voucherType,
            voucherDateFrom,
            voucherDateTo,
            minimumAmount,
            maximumAmount,
            submittedBy,
            page,
            size,
            sortColumn,
            sortDirection
        ), ReportAccessScope.from(authentication)));
    }

    @GetMapping("/authorisation-queue/{voucherHeaderId}")
    public ResponseEntity<VoucherReviewDto> review(@PathVariable Long voucherHeaderId, Authentication authentication) {
        return ResponseEntity.ok(service.review(voucherHeaderId, ReportAccessScope.from(authentication)));
    }

    @GetMapping("/{voucherHeaderId}")
    public ResponseEntity<VoucherDto> find(@PathVariable Long voucherHeaderId, Authentication authentication) {
        return ResponseEntity.ok(service.find(voucherHeaderId, ReportAccessScope.from(authentication)));
    }

    @PutMapping("/{voucherHeaderId}")
    public ResponseEntity<VoucherSaveResponse> update(
        @PathVariable Long voucherHeaderId,
        @Valid @RequestBody VoucherSaveRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.update(voucherHeaderId, request, userId(authentication), ReportAccessScope.from(authentication)));
    }

    @PostMapping("/{voucherHeaderId}/authorise")
    public ResponseEntity<VoucherDto> authorise(@PathVariable Long voucherHeaderId, Authentication authentication) {
        return ResponseEntity.ok(service.authorise(voucherHeaderId, userId(authentication), ReportAccessScope.from(authentication)));
    }

    @PostMapping("/{voucherHeaderId}/reject")
    public ResponseEntity<VoucherDto> reject(
        @PathVariable Long voucherHeaderId,
        @RequestBody(required = false) VoucherActionRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.reject(voucherHeaderId, userId(authentication), request == null ? null : request.reason(), ReportAccessScope.from(authentication)));
    }

    @PostMapping("/{voucherHeaderId}/cancel")
    public ResponseEntity<VoucherDto> cancel(
        @PathVariable Long voucherHeaderId,
        @RequestBody(required = false) VoucherActionRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.cancel(voucherHeaderId, userId(authentication), request == null ? null : request.reason(), ReportAccessScope.from(authentication)));
    }

    @PostMapping("/{voucherHeaderId}/reopen")
    public ResponseEntity<VoucherDto> reopen(
        @PathVariable Long voucherHeaderId,
        @RequestBody(required = false) VoucherActionRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.reopen(voucherHeaderId, userId(authentication), request == null ? null : request.reason(), ReportAccessScope.from(authentication)));
    }

    @PostMapping("/{voucherHeaderId}/resubmit")
    public ResponseEntity<VoucherDto> resubmit(@PathVariable Long voucherHeaderId, Authentication authentication) {
        return ResponseEntity.ok(service.resubmit(voucherHeaderId, userId(authentication), ReportAccessScope.from(authentication)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> validationError(IllegalArgumentException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> requestValidationError(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("Voucher request is invalid.");
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, String>> dataIntegrityError(Exception exception) {
        String message = String.valueOf(exception.getMessage());
        if (message.contains("uq_voucher_headers_voucher_number") || message.contains("voucher_number")) {
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Voucher number already exists."));
        }
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of("message", "Voucher could not be saved because it conflicts with existing data."));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            return null;
        }
        Object userId = details.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        if (userId == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(userId));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
