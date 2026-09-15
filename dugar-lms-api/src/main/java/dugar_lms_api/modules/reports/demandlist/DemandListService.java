package dugar_lms_api.modules.reports.demandlist;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import dugar_lms_api.modules.reports.aginganalysis.BranchWiseAgeingProcedureRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DemandListService {

    static final int PRINT_ROW_LIMIT = 10_000;

    private final DemandListRepository demandListRepository;
    private final DemandListCalculationService demandListCalculationService;
    private final BranchWiseAgeingProcedureRepository branchWiseAgeingProcedureRepository;
    private final ContractAccessRepository contractAccessRepository;

    public DemandListService(
        DemandListRepository demandListRepository,
        DemandListCalculationService demandListCalculationService,
        BranchWiseAgeingProcedureRepository branchWiseAgeingProcedureRepository,
        ContractAccessRepository contractAccessRepository
    ) {
        this.demandListRepository = demandListRepository;
        this.demandListCalculationService = demandListCalculationService;
        this.branchWiseAgeingProcedureRepository = branchWiseAgeingProcedureRepository;
        this.contractAccessRepository = contractAccessRepository;
    }

    public DemandListResponse getDemandList(DemandListRequest request, Authentication authentication) {
        DemandListRequest validated = validate(request, false);
        List<DemandListRowDto> rows = procedureRows(validated, ReportAccessScope.from(authentication));

        return new DemandListResponse(
            page(rows),
            demandListCalculationService.summarize(rows),
            warnings(rows),
            LocalDateTime.now(),
            auditUser(authentication),
            false,
            null
        );
    }

    public DemandListResponse getPrintDemandList(DemandListRequest request, Authentication authentication) {
        DemandListRequest validated = validate(request, true);
        List<DemandListRowDto> rows = procedureRows(validated, ReportAccessScope.from(authentication));
        return new DemandListResponse(
            page(rows),
            demandListCalculationService.summarize(rows),
            warnings(rows),
            LocalDateTime.now(),
            auditUser(authentication),
            false,
            null
        );
    }

    public List<DemandListRowDto> calculateRowsForReport(DemandListRequest request) {
        return calculateRowsForReport(request, new ReportAccessScope(false));
    }

    public List<DemandListRowDto> calculateRowsForReport(DemandListRequest request, ReportAccessScope accessScope) {
        if (request == null || request.asOnDate() == null) {
            throw new IllegalArgumentException("As On Date is required");
        }
        return procedureRows(validate(request, true), accessScope);
    }

    public List<ContractFollowUpDto> getFollowUps(Long contractId, Authentication authentication) {
        requireContractAccess(contractId, authentication);
        return demandListRepository.findFollowUps(contractId);
    }

    public ContractFollowUpDto addFollowUp(Long contractId, ContractFollowUpRequest request, Authentication authentication) {
        requireContractAccess(contractId, authentication);
        if (request == null || request.commentText() == null || request.commentText().trim().isEmpty()) {
            throw new IllegalArgumentException("Comments are mandatory.");
        }
        Long userId = userId(authentication);
        if (userId == null) {
            throw new IllegalArgumentException("User id is required to add a follow-up.");
        }
        return demandListRepository.addFollowUp(
            contractId,
            new ContractFollowUpRequest(request.commentText().trim(), request.followUpDate()),
            userId
        );
    }

    private List<DemandListRowDto> procedureRows(DemandListRequest request, ReportAccessScope accessScope) {
        List<BranchWiseAgeingProcedureRepository.ProcedureContractReportRow> sourceRows =
            branchWiseAgeingProcedureRepository.getContractReportRows(request.asOnDate(), request.areaCode(), accessScope);
        return sourceRows.stream()
            .filter(row -> contractNumberMatches(row, request.contractNumber()))
            .filter(row -> overdueCountMatches(row, request.overdueInstallmentCount()))
            .map(this::demandListRow)
            .toList();
    }

    private List<DemandListRowDto> calculatedRows(DemandListRequest request) {
        List<DemandListSourceRow> sources = demandListRepository.findSourceRows(request);
        Map<Long, List<DemandListRepaymentSlab>> slabsByContract = demandListRepository.findRepaymentSlabs(
            sources.stream().map(DemandListSourceRow::contractId).toList()
        ).stream().collect(Collectors.groupingBy(DemandListRepaymentSlab::contractId));

        return sources.stream()
            .map(source -> demandListCalculationService.calculate(source, slabsByContract.getOrDefault(source.contractId(), List.of()), request.asOnDate()))
            .filter(row -> amountAtLeast(row.overdueAmount(), request.minimumOverdueAmount()))
            .filter(row -> amountAtMost(row.overdueAmount(), request.maximumOverdueAmount()))
            .filter(row -> overdueCountMatches(row, request.overdueInstallmentCount()))
            .toList();
    }

    private DemandListRequest validate(DemandListRequest request, boolean print) {
        if (request == null || request.asOnDate() == null) {
            throw new IllegalArgumentException("As On Date is required");
        }
        return new DemandListRequest(
            request.asOnDate(),
            clean(request.areaCode()),
            clean(request.branchId()),
            clean(request.fieldOfficerCode()),
            clean(request.contractType()),
            clean(request.productType()),
            request.minimumOverdueAmount(),
            request.maximumOverdueAmount(),
            clean(request.contractNumber()),
            validOverdueCount(request.overdueInstallmentCount()),
            clean(request.keyword()),
            0,
            PRINT_ROW_LIMIT,
            clean(request.sortColumn()),
            clean(request.sortDirection())
        );
    }

    private DemandListRowDto demandListRow(BranchWiseAgeingProcedureRepository.ProcedureContractReportRow row) {
        return new DemandListRowDto(
            row.contractId(),
            row.contractNumber(),
            row.contractDate(),
            row.borrowerCode(),
            row.borrowerName(),
            row.borrowerPhone(),
            row.borrowerAddress(),
            row.guarantorCode(),
            row.guarantorName(),
            row.guarantorPhone(),
            row.guarantorAddress(),
            row.guarantor2Code(),
            row.guarantor2Name(),
            row.guarantor2Phone(),
            row.guarantor2Address(),
            first(row.category(), row.contractType()),
            row.vehicleMake(),
            null,
            row.registrationNumber(),
            row.ownerSerialNo(),
            row.contractType(),
            money(row.loanAmount()),
            money(row.flatInterestRate()),
            money(row.totalContractValue()),
            money(row.totalReceived()),
            money(row.principalOutstanding()),
            money(row.interestOutstanding()),
            money(row.totalOutstanding()),
            row.overdueEmiCount(),
            money(row.overdueAmount()),
            row.overdueFromDate(),
            row.overdueEndDate(),
            money(row.currentDue()),
            row.currentDueDate(),
            row.lastPaidEmiDate(),
            row.flagCount(),
            row.flagNames(),
            row.followUpCount(),
            row.areaCode(),
            row.areaName(),
            null,
            null
        );
    }

    private boolean amountAtLeast(BigDecimal value, BigDecimal minimum) {
        return minimum == null || money(value).compareTo(money(minimum)) >= 0;
    }

    private boolean amountAtMost(BigDecimal value, BigDecimal maximum) {
        return maximum == null || money(value).compareTo(money(maximum)) <= 0;
    }

    private boolean overdueCountMatches(DemandListRowDto row, Integer overdueInstallmentCount) {
        return overdueInstallmentCount == null || overdueInstallmentCount.equals(row.overdueInstallmentCount());
    }

    private boolean overdueCountMatches(BranchWiseAgeingProcedureRepository.ProcedureContractReportRow row, Integer overdueInstallmentCount) {
        return overdueInstallmentCount == null || overdueInstallmentCount.equals(row.overdueEmiCount());
    }

    private boolean contractNumberMatches(BranchWiseAgeingProcedureRepository.ProcedureContractReportRow row, String contractNumber) {
        if (contractNumber == null) {
            return true;
        }
        return contractNumber.equalsIgnoreCase(String.valueOf(row.contractNumber()).trim());
    }

    private Integer validOverdueCount(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    private List<String> warnings(List<DemandListRowDto> rows) {
        return rows.stream()
            .map(DemandListRowDto::warning)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
    }

    private PageResponse<DemandListRowDto> page(List<DemandListRowDto> rows) {
        int size = rows.size();
        return new PageResponse<>(rows, 0, size, size, size == 0 ? 0 : 1, true, true, size);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String first(String first, String second) {
        String cleanedFirst = clean(first);
        return cleanedFirst == null ? clean(second) : cleanedFirst;
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

    private void requireContractAccess(Long contractId, Authentication authentication) {
        contractAccessRepository.requireAccess(contractId, ReportAccessScope.from(authentication));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            return null;
        }
        Object userId = details.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        if (userId == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(userId));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
