package dugar_lms_api.modules.contracts.controller;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.contracts.dto.ContractAreaOptionDto;
import dugar_lms_api.modules.contracts.dto.ContractListDto;
import dugar_lms_api.modules.contracts.service.CibilSubmissionExportService;
import dugar_lms_api.modules.contracts.service.CibilSubmissionExportService.ExportResult;
import dugar_lms_api.modules.contracts.service.ContractListCriteria;
import dugar_lms_api.modules.contracts.service.ContractListService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/contracts")
public class ContractListController {

    private final ContractListService contractListService;
    private final CibilSubmissionExportService cibilSubmissionExportService;

    public ContractListController(
        ContractListService contractListService,
        CibilSubmissionExportService cibilSubmissionExportService
    ) {
        this.contractListService = contractListService;
        this.cibilSubmissionExportService = cibilSubmissionExportService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ContractListDto>> listContracts(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String branch,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String product,
        @RequestParam(required = false) String customerName,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractDateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractDateTo,
        @RequestParam(required = false) BigDecimal minimumLoanAmount,
        @RequestParam(required = false) BigDecimal maximumLoanAmount,
        @RequestParam(required = false) Boolean isDraft,
        @RequestParam(required = false) String workflowStatus,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sortColumn,
        @RequestParam(required = false) String sortBy,
        @RequestParam(required = false) String sortDirection,
        Authentication authentication
    ) {
        ContractListCriteria criteria = new ContractListCriteria(
            keyword != null ? keyword : search,
            branch,
            status,
            product,
            customerName,
            contractDateFrom,
            contractDateTo,
            minimumLoanAmount,
            maximumLoanAmount,
            isDraft,
            workflowStatus,
            page,
            size,
            sortColumn != null ? sortColumn : sortBy,
            sortDirection
        );
        return ResponseEntity.ok(contractListService.getContracts(criteria, authentication));
    }

    @GetMapping("/cibil-submission-export")
    public ResponseEntity<byte[]> cibilSubmissionExport(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String branch,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String product,
        @RequestParam(required = false) String customerName,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractDateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate contractDateTo,
        @RequestParam(required = false) BigDecimal minimumLoanAmount,
        @RequestParam(required = false) BigDecimal maximumLoanAmount,
        @RequestParam(required = false) String sortColumn,
        @RequestParam(required = false) String sortBy,
        @RequestParam(required = false) String sortDirection,
        Authentication authentication
    ) {
        ContractListCriteria criteria = new ContractListCriteria(
            keyword != null ? keyword : search,
            branch,
            status,
            product,
            customerName,
            contractDateFrom,
            contractDateTo,
            minimumLoanAmount,
            maximumLoanAmount,
            false,
            null,
            0,
            250,
            sortColumn != null ? sortColumn : sortBy,
            sortDirection
        );
        ExportResult export = cibilSubmissionExportService.export(criteria, authentication);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"CIBIL_Submission.xlsx\"")
            .header("X-Record-Count", String.valueOf(export.recordCount()))
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(export.workbook());
    }

    @GetMapping("/areas")
    public ResponseEntity<java.util.List<ContractAreaOptionDto>> listAreas(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer limit,
        Authentication authentication
    ) {
        return ResponseEntity.ok(contractListService.getAreas(keyword, limit, authentication));
    }

    @GetMapping("/area-master-options")
    public ResponseEntity<java.util.List<ContractAreaOptionDto>> listAreaMasterOptions(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(contractListService.getAreaMasterOptions(keyword, limit));
    }
}
