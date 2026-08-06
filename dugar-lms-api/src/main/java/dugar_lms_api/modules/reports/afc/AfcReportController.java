package dugar_lms_api.modules.reports.afc;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports/afc")
public class AfcReportController {

    private final AfcReportService service;

    public AfcReportController(AfcReportService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AfcReportResponse> getReport(
        @RequestParam String loanNumber,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.getReport(new AfcReportRequest(loanNumber, asOnDate), authentication));
    }

    @GetMapping("/print")
    public ResponseEntity<AfcReportResponse> getPrintReport(
        @RequestParam String loanNumber,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate,
        Authentication authentication
    ) {
        return ResponseEntity.ok(service.getReport(new AfcReportRequest(loanNumber, asOnDate), authentication));
    }
}
