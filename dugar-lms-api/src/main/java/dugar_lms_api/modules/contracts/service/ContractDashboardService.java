package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.ContractDashboardDto;
import dugar_lms_api.modules.contracts.dto.ContractDashboardTotalsDto;
import dugar_lms_api.modules.contracts.repository.ContractDashboardRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.List;

@Service
public class ContractDashboardService {

    private final ContractDashboardRepository contractDashboardRepository;

    public ContractDashboardService(ContractDashboardRepository contractDashboardRepository) {
        this.contractDashboardRepository = contractDashboardRepository;
    }

    public ContractDashboardDto getDashboard(String disbursementPeriod, Integer accountYear) {
        return getDashboard(disbursementPeriod, accountYear, null);
    }

    public ContractDashboardDto getDashboard(String disbursementPeriod, Integer accountYear, Authentication authentication) {
        ReportAccessScope accessScope = ReportAccessScope.from(authentication);
        ContractDashboardTotalsDto totals = contractDashboardRepository.totals(accessScope);
        List<Integer> availableYears = contractDashboardRepository.availableYears(accessScope);
        int currentYear = Year.now().getValue();
        int selectedYear = accountYear == null ? currentYear : accountYear;
        return new ContractDashboardDto(
            totals.totalActiveContracts(),
            totals.totalAum(),
            selectedYear,
            availableYears,
            contractDashboardRepository.branchData(accessScope),
            contractDashboardRepository.disbursementData(disbursementPeriod, selectedYear, accessScope)
        );
    }
}
