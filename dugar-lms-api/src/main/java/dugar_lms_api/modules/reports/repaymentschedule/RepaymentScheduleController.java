package dugar_lms_api.modules.reports.repaymentschedule;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
public class RepaymentScheduleController {
    private final RepaymentScheduleService service;
    private final RepaymentSchedulePdf pdf;
    public RepaymentScheduleController(RepaymentScheduleService service, RepaymentSchedulePdf pdf) {
        this.service = service; this.pdf = pdf;
    }

    @GetMapping("/{contractId}/repayment-schedule/pdf")
    public ResponseEntity<byte[]> generate(@PathVariable long contractId, Authentication authentication) {
        var report = service.load(contractId, authentication);
        try {
            return pdfResponse("Repayment_Schedule_", report.header().contractNumber(), pdf.generate(report));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to generate the repayment schedule PDF.", exception);
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        return errorResponse(exception);
    }

    static ResponseEntity<byte[]> pdfResponse(String prefix, String contractNumber, byte[] bytes) {
        String safeNumber = contractNumber.replaceAll("[^A-Za-z0-9_-]", "_");
        if (safeNumber.length() > 80) safeNumber = safeNumber.substring(0, 80);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                .filename(prefix + safeNumber + ".pdf").build().toString())
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
            .body(bytes);
    }

    static ResponseEntity<Map<String, String>> errorResponse(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
            .cacheControl(CacheControl.noStore())
            .body(Map.of("message", exception.getReason() == null ? "Unable to generate repayment schedule." : exception.getReason()));
    }
}
