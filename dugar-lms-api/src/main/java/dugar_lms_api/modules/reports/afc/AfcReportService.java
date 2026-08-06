package dugar_lms_api.modules.reports.afc;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AfcReportService {

    private final AfcReportRepository repository;
    private final AfcCalculationService calculationService;

    public AfcReportService(AfcReportRepository repository, AfcCalculationService calculationService) {
        this.repository = repository;
        this.calculationService = calculationService;
    }

    public AfcReportResponse getReport(AfcReportRequest request, Authentication authentication) {
        AfcReportRequest validated = validate(request);
        AfcReportSource source = repository.findSource(validated.loanNumber())
            .orElseThrow(() -> new IllegalArgumentException("Loan Number not found"));
        List<AfcRepaymentSlab> slabs = repository.findRepaymentSlabs(source.contractId());
        List<AfcReceipt> receipts = repository.findReceipts(source, validated.asOnDate());
        return new AfcReportResponse(
            calculationService.header(source, validated.asOnDate()),
            calculationService.calculateRows(source, slabs, receipts, validated.asOnDate()),
            calculationService.warnings(source, slabs),
            LocalDateTime.now(),
            auditUser(authentication)
        );
    }

    private AfcReportRequest validate(AfcReportRequest request) {
        if (request == null || request.loanNumber() == null || request.loanNumber().isBlank()) {
            throw new IllegalArgumentException("Loan Number is required");
        }
        if (request.asOnDate() == null) {
            throw new IllegalArgumentException("As On Date is required");
        }
        return new AfcReportRequest(request.loanNumber().trim(), request.asOnDate());
    }

    private String auditUser(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId != null) {
                return String.valueOf(userId);
            }
        }
        return authentication != null && authentication.getName() != null ? authentication.getName() : "system";
    }
}
