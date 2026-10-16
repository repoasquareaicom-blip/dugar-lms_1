package dugar_lms_api.modules.contracts.controller;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.contracts.dto.StockBankMarkingDto;
import dugar_lms_api.modules.contracts.service.StockBankMarkingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/stock-bank-marking/contracts")
public class StockBankMarkingController {
    private final StockBankMarkingService service;
    private final MenuAccessService access;
    public StockBankMarkingController(StockBankMarkingService service, MenuAccessService access) {
        this.service = service; this.access = access;
    }
    public record Request(Long bankId) {}
    private void requireAccess(Authentication authentication) {
        // LMS currently assigns page-level view permissions for transaction screens.
        if (!access.canView("CREDIT_TRANSACTION_STOCK_BANK_MARKING", authentication))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Menu access denied.");
    }
    @GetMapping("/{contractNumber}")
    public StockBankMarkingDto search(@PathVariable String contractNumber, Authentication authentication) {
        requireAccess(authentication);
        return service.search(contractNumber, authentication);
    }
    @PutMapping("/{contractId}")
    public StockBankMarkingDto save(@PathVariable Long contractId, @RequestBody Request request, Authentication authentication) {
        requireAccess(authentication);
        return service.save(contractId, request.bankId(), authentication);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException exception) { return Map.of("message", exception.getMessage()); }
}
