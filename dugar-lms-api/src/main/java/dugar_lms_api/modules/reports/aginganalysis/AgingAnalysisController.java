package dugar_lms_api.modules.reports.aginganalysis;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dugar_lms_api.modules.reports.demandlist.DemandListRowDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports/aging-analysis")
public class AgingAnalysisController {

    private final AgingAnalysisService agingAnalysisService;

    public AgingAnalysisController(AgingAnalysisService agingAnalysisService) {
        this.agingAnalysisService = agingAnalysisService;
    }

    @GetMapping
    public ResponseEntity<AgingAnalysisResponse> getAgingAnalysis(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate,
        @RequestParam(required = false) String areaCode,
        @RequestParam(required = false) String contractNumber,
        Authentication authentication
    ) {
        return ResponseEntity.ok(agingAnalysisService.getAgingAnalysis(new AgingAnalysisRequest(asOnDate, areaCode, contractNumber), authentication));
    }

    @GetMapping("/loan-ticket-wise")
    public ResponseEntity<AgingAnalysisMatrixResponse> getLoanTicketWise(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate,
        @RequestParam(required = false) String areaCode,
        Authentication authentication
    ) {
        return ResponseEntity.ok(agingAnalysisService.getLoanTicketWise(asOnDate, areaCode, authentication));
    }

    @GetMapping("/interest-wise")
    public ResponseEntity<AgingAnalysisMatrixResponse> getInterestWise(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate,
        @RequestParam(required = false) String areaCode,
        Authentication authentication
    ) {
        return ResponseEntity.ok(agingAnalysisService.getInterestWise(asOnDate, areaCode, authentication));
    }

    @GetMapping("/contracts")
    public ResponseEntity<List<DemandListRowDto>> getContracts(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate,
        @RequestParam String areaCode,
        @RequestParam(required = false) String bucket
    ) {
        return ResponseEntity.ok(agingAnalysisService.getContracts(asOnDate, areaCode, bucket));
    }

    @GetMapping("/contracts/{contractId}")
    public ResponseEntity<AgingAnalysisContractDetailDto> getContractDetail(
        @PathVariable Long contractId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate
    ) {
        return ResponseEntity.ok(agingAnalysisService.getContractDetail(contractId, asOnDate));
    }

    @GetMapping("/contracts/{contractId}/emis")
    public ResponseEntity<List<AgingAnalysisEmiDto>> getEmis(
        @PathVariable Long contractId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate
    ) {
        return ResponseEntity.ok(agingAnalysisService.getEmis(contractId, asOnDate));
    }

    @GetMapping("/contracts/{contractId}/receipts")
    public ResponseEntity<List<AgingAnalysisReceiptDto>> getReceipts(
        @PathVariable Long contractId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate
    ) {
        return ResponseEntity.ok(agingAnalysisService.getReceipts(contractId, asOnDate));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> validationError(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", exception.getMessage()));
    }
}
