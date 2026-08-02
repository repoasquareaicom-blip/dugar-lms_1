package dugar_lms_api.modules.contracts.controller;

import dugar_lms_api.modules.contracts.dto.ContractDashboardDto;
import dugar_lms_api.modules.contracts.service.ContractDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contracts/dashboard")
public class ContractDashboardController {

    private final ContractDashboardService contractDashboardService;

    public ContractDashboardController(ContractDashboardService contractDashboardService) {
        this.contractDashboardService = contractDashboardService;
    }

    @GetMapping
    public ResponseEntity<ContractDashboardDto> getDashboard(
        @RequestParam(defaultValue = "YTD") String disbursementPeriod,
        @RequestParam(required = false) Integer accountYear
    ) {
        return ResponseEntity.ok(contractDashboardService.getDashboard(disbursementPeriod, accountYear));
    }
}
