package dugar_lms_api.modules.accessmanagement.controller;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accessmanagement.dto.ContractOptionDto;
import dugar_lms_api.modules.accessmanagement.dto.UserManagementDto;
import dugar_lms_api.modules.accessmanagement.dto.UserManagementRequest;
import dugar_lms_api.modules.accessmanagement.service.UserManagementCriteria;
import dugar_lms_api.modules.accessmanagement.service.UserManagementService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/access-management/users")
public class UserManagementController {

    private final UserManagementService service;

    public UserManagementController(UserManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserManagementDto>> find(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean isActive,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sortColumn,
        @RequestParam(required = false) String sortDirection
    ) {
        return ResponseEntity.ok(service.find(new UserManagementCriteria(keyword, isActive, page, size, sortColumn, sortDirection)));
    }

    @GetMapping("/active-contracts")
    public ResponseEntity<List<ContractOptionDto>> activeContracts(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(service.activeContracts(keyword, limit));
    }

    @PostMapping
    public ResponseEntity<UserManagementDto> create(@Valid @RequestBody UserManagementRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserManagementDto> update(@PathVariable Long userId, @Valid @RequestBody UserManagementRequest request) {
        return ResponseEntity.ok(service.update(userId, request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> requestValidationError(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("User request is invalid.");
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", message));
    }

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, String>> duplicateUser(Exception exception) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of("message", "Login ID already exists."));
    }
}
