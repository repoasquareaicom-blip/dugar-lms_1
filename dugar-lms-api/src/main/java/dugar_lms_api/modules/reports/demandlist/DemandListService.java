package dugar_lms_api.modules.reports.demandlist;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import dugar_lms_api.modules.reports.aginganalysis.BranchWiseAgeingProcedureRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
        return getComments(contractId, authentication);
    }

    public List<ContractFollowUpDto> getComments(Long contractId, Authentication authentication) {
        requireContractAccess(contractId, authentication);
        return demandListRepository.findCommentHistory(contractId, requireUserId(authentication, "load comments"));
    }

    public ContractFollowUpDto addFollowUp(Long contractId, ContractFollowUpRequest request, Authentication authentication) {
        return addComment(contractId, request, authentication);
    }

    public ContractFollowUpDto addComment(Long contractId, ContractFollowUpRequest request, Authentication authentication) {
        requireContractAccess(contractId, authentication);
        if (request == null || request.commentText() == null || request.commentText().trim().isEmpty()) {
            throw new IllegalArgumentException("Comments are mandatory.");
        }
        Long userId = requireUserId(authentication, "add a comment");
        return demandListRepository.addComment(contractId, request.commentText().trim(), userId);
    }

    public List<ContractPtpDto> getPtps(Long contractId, Authentication authentication) {
        requireContractAccess(contractId, authentication);
        return demandListRepository.findPtpHistory(contractId, requireUserId(authentication, "load PTPs"));
    }

    public ContractPtpDto addPtp(Long contractId, ContractPtpRequest request, Authentication authentication) {
        requireContractAccess(contractId, authentication);
        if (request == null || request.ptpDate() == null) {
            throw new IllegalArgumentException("PTP date is required.");
        }
        return demandListRepository.addPtp(contractId, request, requireUserId(authentication, "add a PTP"));
    }

    private List<DemandListRowDto> procedureRows(DemandListRequest request, ReportAccessScope accessScope) {
        List<BranchWiseAgeingProcedureRepository.ProcedureContractReportRow> sourceRows =
            branchWiseAgeingProcedureRepository.getContractReportRows(request.asOnDate(), request.areaCode(), accessScope);
        List<BranchWiseAgeingProcedureRepository.ProcedureContractReportRow> filteredRows = sourceRows.stream()
            .filter(row -> contractNumberMatches(row, request.contractNumber()))
            .filter(row -> overdueCountMatches(row, request.overdueInstallmentCount()))
            .filter(row -> reportTypeMatches(row, request.reportType()))
            .toList();
        return sortedRows(demandListRows(filteredRows, accessScope.userId()), request);
    }

    private List<DemandListRowDto> demandListRows(List<BranchWiseAgeingProcedureRepository.ProcedureContractReportRow> sourceRows, Long userId) {
        List<Long> contractIds = sourceRows.stream()
            .map(BranchWiseAgeingProcedureRepository.ProcedureContractReportRow::contractId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, ContractFollowUpDto> latestComments = userId == null
            ? Map.of()
            : demandListRepository.findLatestComments(contractIds, userId);
        Map<Long, ContractPtpDto> latestPtps = userId == null
            ? Map.of()
            : demandListRepository.findLatestPtps(contractIds, userId);

        return sourceRows.stream()
            .map(row -> demandListRow(row, latestComments.get(row.contractId()), latestPtps.get(row.contractId())))
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

    private List<DemandListRowDto> sortedRows(List<DemandListRowDto> rows, DemandListRequest request) {
        List<DemandListRowDto> sorted = new ArrayList<>(rows);
        sorted.sort(sortComparator(sortColumn(request.sortColumn()), sortDirection(request.sortDirection())));
        return sorted;
    }

    private Comparator<DemandListRowDto> sortComparator(String sortColumn, String sortDirection) {
        boolean ascending = "asc".equals(sortDirection);
        return switch (sortColumn) {
            case "overdueAmount" -> ascending
                ? Comparator.comparing(row -> money(row.overdueAmount()))
                : Comparator.comparing((DemandListRowDto row) -> money(row.overdueAmount())).reversed();
            case "contractNumber" -> {
                Comparator<ContractSortKey> contractComparator = ascending
                    ? Comparator.naturalOrder()
                    : ContractSortKey.descendingComparator();
                yield Comparator
                    .comparing((DemandListRowDto row) -> contractSortKey(row.loanNumber()), contractComparator)
                    .thenComparing(DemandListRowDto::contractId, Comparator.nullsLast(Comparator.reverseOrder()));
            }
            default -> ascending
                ? Comparator.comparing(row -> row.overdueInstallmentCount() == null ? 0 : row.overdueInstallmentCount())
                : Comparator.comparing((DemandListRowDto row) -> row.overdueInstallmentCount() == null ? 0 : row.overdueInstallmentCount()).reversed();
        };
    }

    private ContractSortKey contractSortKey(String contractNumber) {
        String value = clean(contractNumber);
        if (value == null) {
            return new ContractSortKey(2, BigInteger.ZERO, "");
        }
        if (value.matches("\\d+")) {
            return new ContractSortKey(0, new BigInteger(value), value);
        }
        String upper = value.toUpperCase();
        if (upper.matches("L-\\d+")) {
            return new ContractSortKey(1, new BigInteger(upper.substring(2)), upper);
        }
        return new ContractSortKey(2, BigInteger.ZERO, upper);
    }

    private record ContractSortKey(int group, BigInteger number, String text) implements Comparable<ContractSortKey> {
        private ContractSortKey {
            number = number == null ? BigInteger.ZERO : number;
            text = text == null ? "" : text;
        }

        @Override
        public int compareTo(ContractSortKey other) {
            int groupCompare = Integer.compare(group, other.group);
            if (groupCompare != 0) {
                return groupCompare;
            }
            int numberCompare = number.compareTo(other.number);
            if (numberCompare != 0) {
                return numberCompare;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(text, other.text);
        }

        static Comparator<ContractSortKey> descendingComparator() {
            return Comparator
                .comparingInt((ContractSortKey key) -> switch (key.group) {
                    case 1 -> 0;
                    case 0 -> 1;
                    default -> 2;
                })
                .thenComparing(ContractSortKey::number, Comparator.reverseOrder())
                .thenComparing(ContractSortKey::text, String.CASE_INSENSITIVE_ORDER.reversed());
        }
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
            reportType(request.reportType()),
            clean(request.keyword()),
            0,
            PRINT_ROW_LIMIT,
            sortColumn(request.sortColumn()),
            sortDirection(request.sortDirection())
        );
    }

    private DemandListRowDto demandListRow(BranchWiseAgeingProcedureRepository.ProcedureContractReportRow row, ContractFollowUpDto latestComment, ContractPtpDto latestPtp) {
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
            row.productType(),
            row.vehicleMake(),
            row.vehicleTypeCode(),
            row.registrationNumber(),
            row.ownerSerialNo(),
            row.vehicleTypeCode(),
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
            row.flagCodes(),
            row.flagRemarks(),
            row.followUpCount(),
            latestComment == null ? null : latestComment.commentText(),
            latestComment == null ? null : latestComment.createdAt(),
            latestPtp == null ? null : latestPtp.ptpDate(),
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

    private boolean reportTypeMatches(BranchWiseAgeingProcedureRepository.ProcedureContractReportRow row, String reportType) {
        return switch (reportType(reportType)) {
            case "THREE_DUES_ABOVE" -> row.overdueEmiCount() != null && row.overdueEmiCount() >= 3;
            case "REPOSSESSED_STOCK" -> hasFlagCode(row, "REPOSSESSED_VEHICLE");
            case "LITIGATION_MATTERS" -> hasFlagCode(row, "LITIGATION");
            default -> true;
        };
    }

    private boolean hasFlagCode(BranchWiseAgeingProcedureRepository.ProcedureContractReportRow row, String flagCode) {
        if (row.flagCodes() == null || flagCode == null) {
            return false;
        }
        for (String code : row.flagCodes().split(",")) {
            if (flagCode.equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    private String reportType(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return "CONSOLIDATED";
        }
        String normalized = cleaned.trim().toUpperCase().replace('-', '_').replace(' ', '_').replace('&', '_');
        return switch (normalized) {
            case "THREE_DUES_ABOVE", "3_DUES_ABOVE" -> "THREE_DUES_ABOVE";
            case "REPOSSESSED_STOCK" -> "REPOSSESSED_STOCK";
            case "LITIGATION_MATTERS" -> "LITIGATION_MATTERS";
            case "FULL_NAME_ADDRESS", "FULL_NAME_AND_ADDRESS" -> "FULL_NAME_ADDRESS";
            default -> "CONSOLIDATED";
        };
    }

    private String sortColumn(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return "overdueInstallmentCount";
        }
        return switch (cleaned) {
            case "overdueInstallmentCount", "count", "overdueCount" -> "overdueInstallmentCount";
            case "overdueAmount", "amount" -> "overdueAmount";
            case "contractNumber", "loanNumber" -> "contractNumber";
            default -> "overdueInstallmentCount";
        };
    }

    private String sortDirection(String value) {
        String cleaned = clean(value);
        return cleaned != null && (cleaned.equalsIgnoreCase("asc") || cleaned.equalsIgnoreCase("ascending")) ? "asc" : "desc";
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

    private Long requireUserId(Authentication authentication, String action) {
        Long userId = userId(authentication);
        if (userId == null) {
            throw new IllegalArgumentException("User id is required to " + action + ".");
        }
        return userId;
    }
}
