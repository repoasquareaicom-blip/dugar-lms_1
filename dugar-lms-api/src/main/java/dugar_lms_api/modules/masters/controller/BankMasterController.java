package dugar_lms_api.modules.masters.controller;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.masters.dto.BankMasterDto;
import dugar_lms_api.modules.masters.dto.BankMasterRequest;
import dugar_lms_api.modules.masters.service.BankMasterCriteria;
import dugar_lms_api.modules.masters.service.BankMasterService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bank-masters")
public class BankMasterController {

    private final BankMasterService service;

    public BankMasterController(BankMasterService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<BankMasterDto>> find(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean isActive,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sortColumn,
        @RequestParam(required = false) String sortDirection
    ) {
        return ResponseEntity.ok(service.find(new BankMasterCriteria(keyword, isActive, page, size, sortColumn, sortDirection)));
    }

    @GetMapping("/active")
    public ResponseEntity<List<BankMasterDto>> active() {
        return ResponseEntity.ok(service.findActive());
    }

    @PostMapping
    public ResponseEntity<BankMasterDto> create(@Valid @RequestBody BankMasterRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BankMasterDto> update(@PathVariable Long id, @Valid @RequestBody BankMasterRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, String>> duplicateBankCode(Exception exception) {
        String detail = exception.getMessage() == null ? "" : exception.getMessage();
        if (!detail.toLowerCase().contains("bank")) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Unable to save bank master. Please check the entered values."));
        }
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of("message", "Bank code already exists."));
    }
}
