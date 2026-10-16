package dugar_lms_api.modules.reports.stockstatement;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.reports.ReportAccessScope;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/stock-statement")
public class StockStatementController {
    private final StockStatementService service;
    private final StockStatementExcel excel;
    private final MenuAccessService access;
    public StockStatementController(StockStatementService service, StockStatementExcel excel, MenuAccessService access) {
        this.service = service; this.excel = excel; this.access = access;
    }

    @GetMapping("/business-date")
    public Map<String, LocalDate> businessDate(Authentication authentication) {
        requireAccess(authentication);
        return Map.of("businessDate", service.businessDate());
    }

    @GetMapping
    public StockStatementResponse generate(@RequestParam Long bankId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate, Authentication authentication) {
        requireAccess(authentication);
        return service.generate(bankId, asOnDate, ReportAccessScope.from(authentication));
    }

    @GetMapping("/export")
    public void export(@RequestParam Long bankId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate, @RequestParam String fingerprint,
                       Authentication authentication, HttpServletResponse response) throws IOException {
        var report = generate(bankId, asOnDate, authentication);
        if (!report.fingerprint().equals(fingerprint)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report data changed. Generate the report again before downloading.");
        }
        String filename = "Stock_Statement_" + report.bankCode().replaceAll("[^A-Za-z0-9_-]", "_") + "_"
            + report.businessDate().format(DateTimeFormatter.ofPattern("ddMMyyyy")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        excel.write(report, response.getOutputStream());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException exception) { return Map.of("message", exception.getMessage()); }

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode())
            .body(Map.of("message", exception.getReason() == null ? "Report request failed." : exception.getReason()));
    }

    private void requireAccess(Authentication authentication) {
        if (!access.canView("CREDIT_REPORTS_STOCK_STATEMENT", authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Menu access denied.");
        }
    }
}
