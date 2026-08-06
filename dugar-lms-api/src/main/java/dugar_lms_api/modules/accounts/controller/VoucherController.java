package dugar_lms_api.modules.accounts.controller;

import dugar_lms_api.modules.accounts.dto.VoucherDto;
import dugar_lms_api.modules.accounts.dto.VoucherSaveRequest;
import dugar_lms_api.modules.accounts.dto.VoucherSaveResponse;
import dugar_lms_api.modules.accounts.dto.VoucherSummaryDto;
import dugar_lms_api.modules.accounts.service.VoucherService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        @RequestParam(required = false) String contractNumber
    ) {
        return ResponseEntity.ok(service.search(voucherType, transactionType, voucherNumber, voucherDate, contractNumber));
    }

    @GetMapping("/{voucherHeaderId}")
    public ResponseEntity<VoucherDto> find(@PathVariable Long voucherHeaderId) {
        return ResponseEntity.ok(service.find(voucherHeaderId));
    }

    @PutMapping("/{voucherHeaderId}")
    public ResponseEntity<VoucherSaveResponse> update(
        @PathVariable Long voucherHeaderId,
        @Valid @RequestBody VoucherSaveRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.update(voucherHeaderId, request, userId(authentication)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> validationError(IllegalArgumentException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", exception.getMessage()));
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
