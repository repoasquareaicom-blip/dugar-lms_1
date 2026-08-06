package dugar_lms_api.modules.reports.demandlist;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports/demand-list")
public class DemandListController {

    private final DemandListService demandListService;

    public DemandListController(DemandListService demandListService) {
        this.demandListService = demandListService;
    }

    @GetMapping
    public ResponseEntity<DemandListResponse> getDemandList(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate,
        @RequestParam(required = false) String areaCode,
        @RequestParam(required = false) String branchId,
        @RequestParam(required = false) String fieldOfficerCode,
        @RequestParam(required = false) String contractType,
        @RequestParam(required = false) String productType,
        @RequestParam(required = false) BigDecimal minimumOverdueAmount,
        @RequestParam(required = false) BigDecimal maximumOverdueAmount,
        @RequestParam(required = false) String contractNumber,
        @RequestParam(required = false) Integer overdueInstallmentCount,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sortColumn,
        @RequestParam(required = false) String sortDirection,
        Authentication authentication
    ) {
        return ResponseEntity.ok(demandListService.getDemandList(request(asOnDate, areaCode, branchId, fieldOfficerCode, contractType, productType, minimumOverdueAmount, maximumOverdueAmount, contractNumber, overdueInstallmentCount, keyword, page, size, sortColumn, sortDirection), authentication));
    }

    @GetMapping("/print")
    public ResponseEntity<DemandListResponse> getPrintDemandList(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOnDate,
        @RequestParam(required = false) String areaCode,
        @RequestParam(required = false) String branchId,
        @RequestParam(required = false) String fieldOfficerCode,
        @RequestParam(required = false) String contractType,
        @RequestParam(required = false) String productType,
        @RequestParam(required = false) BigDecimal minimumOverdueAmount,
        @RequestParam(required = false) BigDecimal maximumOverdueAmount,
        @RequestParam(required = false) String contractNumber,
        @RequestParam(required = false) Integer overdueInstallmentCount,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String sortColumn,
        @RequestParam(required = false) String sortDirection,
        Authentication authentication
    ) {
        return ResponseEntity.ok(demandListService.getPrintDemandList(request(asOnDate, areaCode, branchId, fieldOfficerCode, contractType, productType, minimumOverdueAmount, maximumOverdueAmount, contractNumber, overdueInstallmentCount, keyword, 0, DemandListService.PRINT_ROW_LIMIT, sortColumn, sortDirection), authentication));
    }

    private DemandListRequest request(
        LocalDate asOnDate,
        String areaCode,
        String branchId,
        String fieldOfficerCode,
        String contractType,
        String productType,
        BigDecimal minimumOverdueAmount,
        BigDecimal maximumOverdueAmount,
        String contractNumber,
        Integer overdueInstallmentCount,
        String keyword,
        Integer page,
        Integer size,
        String sortColumn,
        String sortDirection
    ) {
        return new DemandListRequest(asOnDate, areaCode, branchId, fieldOfficerCode, contractType, productType, minimumOverdueAmount, maximumOverdueAmount, contractNumber, overdueInstallmentCount, keyword, page, size, sortColumn, sortDirection);
    }
}
