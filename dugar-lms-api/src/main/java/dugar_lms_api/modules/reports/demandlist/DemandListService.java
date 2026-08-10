package dugar_lms_api.modules.reports.demandlist;

import dugar_lms_api.common.pagination.PageResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DemandListService {

    static final int DEFAULT_PAGE = 0;
    static final int DEFAULT_SIZE = 25;
    static final int MAX_SIZE = 250;
    static final int PRINT_ROW_LIMIT = 10_000;

    private final DemandListRepository demandListRepository;
    private final DemandListCalculationService demandListCalculationService;

    public DemandListService(
        DemandListRepository demandListRepository,
        DemandListCalculationService demandListCalculationService
    ) {
        this.demandListRepository = demandListRepository;
        this.demandListCalculationService = demandListCalculationService;
    }

    public DemandListResponse getDemandList(DemandListRequest request, Authentication authentication) {
        DemandListRequest validated = validate(request, false);
        List<DemandListRowDto> filteredRows = calculatedRows(validated);
        List<DemandListRowDto> sortedRows = sortRows(filteredRows, validated.sortColumn(), validated.sortDirection());
        int page = page(validated.page());
        int size = size(validated.size());
        int from = Math.min(page * size, sortedRows.size());
        int to = Math.min(from + size, sortedRows.size());
        List<DemandListRowDto> pageRows = sortedRows.subList(from, to);
        int totalPages = sortedRows.isEmpty() ? 0 : (int) Math.ceil((double) sortedRows.size() / size);

        return new DemandListResponse(
            new PageResponse<>(pageRows, page, size, sortedRows.size(), totalPages, page == 0, totalPages == 0 || page >= totalPages - 1, pageRows.size()),
            demandListCalculationService.summarize(filteredRows),
            warnings(filteredRows),
            LocalDateTime.now(),
            auditUser(authentication),
            false,
            null
        );
    }

    public DemandListResponse getPrintDemandList(DemandListRequest request, Authentication authentication) {
        DemandListRequest validated = validate(request, true);
        List<DemandListRowDto> filteredRows = calculatedRows(validated);
        if (filteredRows.size() > PRINT_ROW_LIMIT) {
            return new DemandListResponse(
                new PageResponse<>(List.of(), 0, PRINT_ROW_LIMIT, filteredRows.size(), 1, true, true, 0),
                demandListCalculationService.summarize(filteredRows),
                warnings(filteredRows),
                LocalDateTime.now(),
                auditUser(authentication),
                true,
                "Demand List contains more than 10,000 rows. Refine the filters before printing."
            );
        }

        List<DemandListRowDto> sortedRows = sortRows(filteredRows, validated.sortColumn(), validated.sortDirection());
        return new DemandListResponse(
            new PageResponse<>(sortedRows, 0, sortedRows.size(), sortedRows.size(), sortedRows.isEmpty() ? 0 : 1, true, true, sortedRows.size()),
            demandListCalculationService.summarize(filteredRows),
            warnings(filteredRows),
            LocalDateTime.now(),
            auditUser(authentication),
            false,
            null
        );
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
        if (clean(request.areaCode()) == null && clean(request.contractNumber()) == null) {
            throw new IllegalArgumentException("Area or Contract No is required");
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
            print ? 0 : page(request.page()),
            print ? PRINT_ROW_LIMIT : size(request.size()),
            clean(request.sortColumn()),
            clean(request.sortDirection())
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

    private List<DemandListRowDto> sortRows(List<DemandListRowDto> rows, String sortColumn, String sortDirection) {
        Comparator<DemandListRowDto> comparator = comparator(sortColumn);
        if ("desc".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }
        return rows.stream().sorted(comparator.thenComparing(DemandListRowDto::contractId, Comparator.nullsLast(Long::compareTo))).toList();
    }

    private Comparator<DemandListRowDto> comparator(String sortColumn) {
        String normalized = sortColumn == null ? "loanNumber" : sortColumn.trim();
        return switch (normalized) {
            case "borrowerName" -> Comparator.comparing(DemandListRowDto::borrowerName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "productType" -> Comparator.comparing(DemandListRowDto::productType, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "contractValue" -> Comparator.comparing(row -> money(row.contractValue()));
            case "principalOutstanding" -> Comparator.comparing(row -> money(row.principalOutstanding()));
            case "interestOutstanding" -> Comparator.comparing(row -> money(row.interestOutstanding()));
            case "totalOutstanding" -> Comparator.comparing(row -> money(row.totalOutstanding()));
            case "overdueInstallmentCount" -> Comparator.comparing(row -> row.overdueInstallmentCount() == null ? 0 : row.overdueInstallmentCount());
            case "overdueAmount" -> Comparator.comparing(row -> money(row.overdueAmount()));
            case "currentDueAmount" -> Comparator.comparing(row -> money(row.currentDueAmount()));
            case "area" -> Comparator.comparing(DemandListRowDto::area, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "fieldOfficer" -> Comparator.comparing(DemandListRowDto::fieldOfficer, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            default -> Comparator.comparing(DemandListRowDto::loanNumber, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        };
    }

    private int page(Integer page) {
        return page == null || page < 0 ? DEFAULT_PAGE : page;
    }

    private int size(Integer size) {
        return size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
