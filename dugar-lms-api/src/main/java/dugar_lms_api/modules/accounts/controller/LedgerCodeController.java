package dugar_lms_api.modules.accounts.controller;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accounts.dto.LedgerActiveRequest;
import dugar_lms_api.modules.accounts.dto.LedgerCodeDto;
import dugar_lms_api.modules.accounts.dto.LedgerCodeRequest;
import dugar_lms_api.modules.accounts.service.LedgerCodeCriteria;
import dugar_lms_api.modules.accounts.service.LedgerCodeService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/accounts/ledger-codes")
public class LedgerCodeController {

    private final LedgerCodeService service;

    public LedgerCodeController(LedgerCodeService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<LedgerCodeDto>> find(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean isActive,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sortColumn,
        @RequestParam(required = false) String sortDirection
    ) {
        return ResponseEntity.ok(service.find(new LedgerCodeCriteria(keyword, isActive, page, size, sortColumn, sortDirection)));
    }

    @PostMapping
    public ResponseEntity<LedgerCodeDto> create(@Valid @RequestBody LedgerCodeRequest request, Authentication authentication) {
        return ResponseEntity.ok(service.create(request, userId(authentication)));
    }

    @PutMapping("/{ledgerCode}")
    public ResponseEntity<LedgerCodeDto> update(
        @PathVariable String ledgerCode,
        @Valid @RequestBody LedgerCodeRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.update(ledgerCode, request, userId(authentication)));
    }

    @PatchMapping("/{ledgerCode}/active")
    public ResponseEntity<LedgerCodeDto> updateActive(
        @PathVariable String ledgerCode,
        @RequestBody LedgerActiveRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.updateActive(ledgerCode, request, userId(authentication)));
    }

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, String>> duplicateLedgerCode(Exception exception) {
        String detail = exception.getMessage() == null ? "" : exception.getMessage();
        if (!detail.toLowerCase().contains("ledger")) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Unable to save ledger code. Please check the entered values."));
        }
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of("message", "Ledger code already exists."));
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
