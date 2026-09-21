package dugar_lms_api.modules.reports.aginganalysis;

import dugar_lms_api.modules.reports.demandlist.DemandListRequest;
import dugar_lms_api.modules.reports.demandlist.DemandListRowDto;
import dugar_lms_api.modules.reports.demandlist.DemandListService;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgingAnalysisService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final List<AgingBucket> BRANCH_BUCKETS = List.of(
        AgingBucket.CURRENT,
        AgingBucket.DAYS_1_TO_30,
        AgingBucket.DAYS_31_TO_60,
        AgingBucket.DAYS_61_TO_90,
        AgingBucket.DAYS_91_TO_120,
        AgingBucket.DAYS_121_TO_150,
        AgingBucket.DAYS_151_TO_180,
        AgingBucket.ABOVE_180
    );
    private static final List<ConsolidatedBucket> CONSOLIDATED_BUCKETS = List.of(
        ConsolidatedBucket.CURRENT,
        ConsolidatedBucket.DAYS_1_TO_30,
        ConsolidatedBucket.DAYS_31_TO_60,
        ConsolidatedBucket.DAYS_61_TO_120,
        ConsolidatedBucket.DAYS_121_TO_150,
        ConsolidatedBucket.DAYS_151_TO_180,
        ConsolidatedBucket.ABOVE_180
    );

    private final DemandListService demandListService;
    private final BranchWiseAgeingProcedureRepository branchWiseAgeingProcedureRepository;
    private final AgingAnalysisDrilldownRepository drilldownRepository;
    private final ConsolidatedPortfolioRepository consolidatedPortfolioRepository;

    public AgingAnalysisService(
        DemandListService demandListService,
        BranchWiseAgeingProcedureRepository branchWiseAgeingProcedureRepository,
        AgingAnalysisDrilldownRepository drilldownRepository,
        ConsolidatedPortfolioRepository consolidatedPortfolioRepository
    ) {
        this.demandListService = demandListService;
        this.branchWiseAgeingProcedureRepository = branchWiseAgeingProcedureRepository;
        this.drilldownRepository = drilldownRepository;
        this.consolidatedPortfolioRepository = consolidatedPortfolioRepository;
    }

    public AgingAnalysisResponse getAgingAnalysis(AgingAnalysisRequest request, Authentication authentication) {
        AgingAnalysisRequest validated = validateBranchWiseRequest(request);
        if (validated.contractNumber() != null) {
            throw new IllegalArgumentException("Contract No filter is not supported for Branch Wise Ageing stored procedure report yet");
        }
        List<AgingAnalysisBranchRowDto> branchRows =
            branchWiseAgeingProcedureRepository.getBranchWiseRows(validated.asOnDate(), validated.areaCode(), ReportAccessScope.from(authentication));

        return new AgingAnalysisResponse(
            validated.asOnDate(),
            branchRows,
            consolidatedRowsFromBranchRows(branchRows),
            summaryFromBranchRows(branchRows),
            List.of(),
            LocalDateTime.now(),
            auditUser(authentication)
        );
    }

    public AgingAnalysisMatrixResponse getLoanTicketWise(LocalDate asOnDate, String areaCode, Authentication authentication) {
        LocalDate reportDate = asOnDate == null ? LocalDate.now() : asOnDate;
        AgingAnalysisRequest request = validate(new AgingAnalysisRequest(reportDate, areaCode, null));
        List<AgedLoan> agedLoans = procedureAgedLoans(request, ReportAccessScope.from(authentication));
        return new AgingAnalysisMatrixResponse(
            reportDate,
            "Loan Ticket Wise",
            matrixRows(agedLoans, this::loanTicketLabel, this::loanTicketOrder),
            warnings(agedLoans),
            LocalDateTime.now(),
            auditUser(authentication)
        );
    }

    public AgingAnalysisMatrixResponse getInterestWise(LocalDate asOnDate, String areaCode, Authentication authentication) {
        LocalDate reportDate = asOnDate == null ? LocalDate.now() : asOnDate;
        AgingAnalysisRequest request = validate(new AgingAnalysisRequest(reportDate, areaCode, null));
        List<AgedLoan> agedLoans = procedureAgedLoans(request, ReportAccessScope.from(authentication));
        return new AgingAnalysisMatrixResponse(
            reportDate,
            "Interest Wise",
            matrixRows(agedLoans, this::interestLabel, this::interestOrder),
            warnings(agedLoans),
            LocalDateTime.now(),
            auditUser(authentication)
        );
    }

    public ConsolidatedPortfolioResponse getConsolidatedPortfolio(LocalDate asOnDate, String areaCode, Authentication authentication) {
        if (asOnDate == null) {
            throw new IllegalArgumentException("As On Date is required");
        }
        return consolidatedPortfolioRepository.getPortfolio(asOnDate, cleanPortfolioArea(areaCode), ReportAccessScope.from(authentication));
    }

    public List<DemandListRowDto> getContracts(LocalDate asOnDate, String areaCode, String bucket, Authentication authentication) {
        AgingAnalysisRequest request = validate(new AgingAnalysisRequest(asOnDate == null ? LocalDate.now() : asOnDate, areaCode, null));
        DemandListRequest demandRequest = new DemandListRequest(
            request.asOnDate(),
            request.areaCode(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            DemandListServiceDefaults.PRINT_ROW_LIMIT,
            null,
            null
        );
        return demandListService.calculateRowsForReport(demandRequest, ReportAccessScope.from(authentication)).stream()
            .filter(row -> bucketMatches(row.overdueInstallmentCount(), bucket))
            .toList();
    }

    private List<AgedLoan> procedureAgedLoans(AgingAnalysisRequest request, ReportAccessScope accessScope) {
        List<BranchWiseAgeingProcedureRepository.ProcedureContractReportRow> rows =
            branchWiseAgeingProcedureRepository.getContractReportRows(request.asOnDate(), request.areaCode(), accessScope);
        return rows.stream()
            .map(row -> procedureAgedLoan(row, request.asOnDate()))
            .toList();
    }

    public AgingAnalysisContractDetailDto getContractDetail(Long contractId, LocalDate asOnDate, Authentication authentication) {
        LocalDate reportDate = asOnDate == null ? LocalDate.now() : asOnDate;
        AgingAnalysisDrilldownRepository.ContractSource source = contractSource(contractId, ReportAccessScope.from(authentication));
        List<AgingAnalysisReceiptDto> receipts = drilldownRepository.findReceipts(source.contractNumber(), reportDate);
        BigDecimal totalReceived = receipts.stream().map(AgingAnalysisReceiptDto::collectionAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal totalContractValue = money(source.totalContractValue());
        BigDecimal financeCharges = money(source.financeCharges());
        BigDecimal originalPrincipal = totalContractValue.subtract(financeCharges).max(ZERO);
        BigDecimal principalRecovered = proportional(totalReceived, originalPrincipal, totalContractValue);
        BigDecimal aum = originalPrincipal.subtract(principalRecovered).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalOutstanding = totalContractValue.subtract(totalReceived).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        int overdueCount = overdueCount(emis(source, receipts, reportDate), reportDate);

        return new AgingAnalysisContractDetailDto(
            source.contractId(),
            source.contractNumber(),
            source.areaCode(),
            source.areaName(),
            source.contractDate(),
            source.firstEmiDate(),
            totalContractValue,
            financeCharges,
            originalPrincipal,
            money(totalReceived),
            principalRecovered,
            aum,
            totalOutstanding,
            overdueCount,
            bucketLabel(overdueCount)
        );
    }

    public List<AgingAnalysisEmiDto> getEmis(Long contractId, LocalDate asOnDate, Authentication authentication) {
        LocalDate reportDate = asOnDate == null ? LocalDate.now() : asOnDate;
        AgingAnalysisDrilldownRepository.ContractSource source = contractSource(contractId, ReportAccessScope.from(authentication));
        return emis(source, drilldownRepository.findReceipts(source.contractNumber(), reportDate), reportDate);
    }

    public List<AgingAnalysisReceiptDto> getReceipts(Long contractId, LocalDate asOnDate, Authentication authentication) {
        LocalDate reportDate = asOnDate == null ? LocalDate.now() : asOnDate;
        AgingAnalysisDrilldownRepository.ContractSource source = contractSource(contractId, ReportAccessScope.from(authentication));
        return drilldownRepository.findReceipts(source.contractNumber(), reportDate);
    }

    public AgingAnalysisRawVoucherDto getRawVoucher(Long contractId, String voucherType, String voucherNumber, Authentication authentication) {
        AgingAnalysisDrilldownRepository.ContractSource source = contractSource(contractId, ReportAccessScope.from(authentication));
        return drilldownRepository.findRawVoucher(source.contractNumber(), voucherType, voucherNumber);
    }

    private List<AgedLoan> agedLoans(AgingAnalysisRequest request) {
        DemandListRequest demandRequest = new DemandListRequest(
            request.asOnDate(),
            request.areaCode(),
            null,
            null,
            null,
            null,
            null,
            null,
            request.contractNumber(),
            null,
            null,
            null,
            0,
            DemandListServiceDefaults.PRINT_ROW_LIMIT,
            null,
            null
        );
        return demandListService.calculateRowsForReport(demandRequest).stream()
            .map(row -> agedLoan(row, request.asOnDate()))
            .toList();
    }

    private AgingAnalysisDrilldownRepository.ContractSource contractSource(Long contractId) {
        return contractSource(contractId, new ReportAccessScope(false));
    }

    private AgingAnalysisDrilldownRepository.ContractSource contractSource(Long contractId, ReportAccessScope accessScope) {
        if (contractId == null) {
            throw new IllegalArgumentException("Contract is required");
        }
        AgingAnalysisDrilldownRepository.ContractSource source = drilldownRepository.findContract(contractId, accessScope);
        if (source == null) {
            throw new IllegalArgumentException("Contract not found");
        }
        return source;
    }

    private List<AgingAnalysisEmiDto> emis(
        AgingAnalysisDrilldownRepository.ContractSource source,
        List<AgingAnalysisReceiptDto> receipts,
        LocalDate asOnDate
    ) {
        List<ScheduleItem> schedule = schedule(source.firstEmiDate(), source.paymentFrequency(), drilldownRepository.findRepaymentSlabs(source.contractId()));
        BigDecimal remainingReceipts = receipts.stream()
            .map(AgingAnalysisReceiptDto::collectionAmount)
            .reduce(ZERO, BigDecimal::add);
        List<AgingAnalysisEmiDto> rows = new ArrayList<>();

        for (int index = 0; index < schedule.size(); index++) {
            ScheduleItem item = schedule.get(index);
            BigDecimal paid = ZERO;
            if (remainingReceipts.compareTo(ZERO) > 0) {
                paid = remainingReceipts.min(item.amount());
                remainingReceipts = remainingReceipts.subtract(paid).setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal outstanding = item.amount().subtract(paid).max(ZERO).setScale(2, RoundingMode.HALF_UP);
            rows.add(new AgingAnalysisEmiDto(
                index + 1,
                item.dueDate(),
                item.amount(),
                paid,
                outstanding,
                emiStatus(item.dueDate(), item.amount(), paid, asOnDate)
            ));
        }
        return rows;
    }

    private List<ScheduleItem> schedule(
        LocalDate firstEmiDate,
        String frequency,
        List<AgingAnalysisDrilldownRepository.RepaymentSlab> slabs
    ) {
        if (firstEmiDate == null || slabs == null || slabs.isEmpty()) {
            return List.of();
        }
        int months = frequencyMonths(frequency);
        LocalDate dueDate = firstEmiDate;
        List<ScheduleItem> items = new ArrayList<>();
        for (AgingAnalysisDrilldownRepository.RepaymentSlab slab : slabs.stream()
            .sorted(Comparator.comparing(row -> row.sequenceNo() == null ? 0 : row.sequenceNo()))
            .toList()) {
            int count = slab.numberOfInstallments() == null ? 0 : slab.numberOfInstallments();
            BigDecimal amount = money(slab.installmentAmount());
            for (int index = 0; index < count; index++) {
                items.add(new ScheduleItem(dueDate, amount));
                dueDate = dueDate.plusMonths(months);
            }
        }
        return items;
    }

    private String emiStatus(LocalDate dueDate, BigDecimal amount, BigDecimal paid, LocalDate asOnDate) {
        if (paid.compareTo(money(amount)) >= 0) return "PAID";
        if (paid.compareTo(ZERO) > 0) return "PART PAID";
        if (dueDate != null && dueDate.isAfter(asOnDate)) return "UNPAID";
        return "UNPAID";
    }

    private int overdueCount(List<AgingAnalysisEmiDto> emis, LocalDate asOnDate) {
        return (int) emis.stream()
            .filter(row -> row.dueDate() != null && !row.dueDate().isAfter(asOnDate))
            .filter(row -> money(row.outstandingAmount()).compareTo(ZERO) > 0)
            .count();
    }

    private boolean bucketMatches(Integer overdueCount, String bucket) {
        String normalized = bucket == null ? "" : bucket.trim().toLowerCase();
        int count = overdueCount == null ? 0 : overdueCount;
        return switch (normalized) {
            case "", "all", "total", "no_of_accounts" -> true;
            case "current" -> count == 0;
            case "1_30", "1-30" -> count == 1;
            case "31_60", "31-60" -> count == 2;
            case "61_90", "61-90" -> count == 3;
            case "91_120", "91-120" -> count == 4;
            case "121_150", "121-150" -> count == 5;
            case "151_180", "151-180" -> count == 6;
            case "above_180", "180_above", "above180" -> count >= 7;
            default -> true;
        };
    }

    private String bucketLabel(int overdueCount) {
        if (overdueCount <= 0) return "Current";
        if (overdueCount == 1) return "1--30";
        if (overdueCount == 2) return "31--60";
        if (overdueCount == 3) return "61--90";
        if (overdueCount == 4) return "91--120";
        if (overdueCount == 5) return "121--150";
        if (overdueCount == 6) return "151--180";
        return "180 & Above";
    }

    private AgedLoan agedLoan(DemandListRowDto row, java.time.LocalDate asOnDate) {
        AgingBucket bucket = bucket(row, asOnDate);
        return new AgedLoan(
            row.contractId(),
            clean(row.areaCode()),
            bucket,
            consolidatedBucket(bucket),
            money(row.loanAmount()),
            money(row.flatInterestRate()),
            money(row.totalOutstanding()),
            money(row.principalOutstanding()),
            money(row.interestOutstanding()),
            overdueAgeDays(row, asOnDate),
            row.warning()
        );
    }

    private AgedLoan procedureAgedLoan(BranchWiseAgeingProcedureRepository.ProcedureContractReportRow row, LocalDate asOnDate) {
        AgingBucket bucket = bucket(row.overdueEmiCount());
        return new AgedLoan(
            row.contractId(),
            clean(row.areaCode()),
            bucket,
            consolidatedBucket(bucket),
            money(row.loanAmount()),
            money(row.flatInterestRate()),
            money(row.totalOutstanding()),
            money(row.principalOutstanding()),
            money(row.interestOutstanding()),
            overdueAgeDays(row.overdueEmiCount(), row.overdueFromDate(), asOnDate),
            null
        );
    }

    private List<AgingAnalysisBranchRowDto> branchRows(List<AgedLoan> agedLoans) {
        Map<String, List<AgedLoan>> byArea = agedLoans.stream()
            .collect(Collectors.groupingBy(AgedLoan::area, LinkedHashMap::new, Collectors.toList()));

        List<AgingAnalysisBranchRowDto> rows = new ArrayList<>();
        for (Map.Entry<String, List<AgedLoan>> entry : byArea.entrySet()) {
            List<AgedLoan> loans = entry.getValue();
            Map<AgingBucket, BigDecimal> totals = totalsByBranchBucket(loans);
            BigDecimal total = BRANCH_BUCKETS.stream()
                .map(bucket -> totals.getOrDefault(bucket, ZERO))
                .reduce(ZERO, BigDecimal::add);

            rows.add(new AgingAnalysisBranchRowDto(
                entry.getKey(),
                null,
                loans.size(),
                sum(loans, AgedLoan::totalOutstanding),
                totals.getOrDefault(AgingBucket.CURRENT, ZERO),
                totals.getOrDefault(AgingBucket.DAYS_1_TO_30, ZERO),
                totals.getOrDefault(AgingBucket.DAYS_31_TO_60, ZERO),
                totals.getOrDefault(AgingBucket.DAYS_61_TO_90, ZERO),
                totals.getOrDefault(AgingBucket.DAYS_91_TO_120, ZERO),
                totals.getOrDefault(AgingBucket.DAYS_121_TO_150, ZERO),
                totals.getOrDefault(AgingBucket.DAYS_151_TO_180, ZERO),
                totals.getOrDefault(AgingBucket.ABOVE_180, ZERO),
                money(total),
                sum(loans, AgedLoan::interestOutstanding)
            ));
        }

        return rows.stream()
            .sorted(Comparator.comparing(AgingAnalysisBranchRowDto::areaCode, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private List<AgingAnalysisConsolidatedRowDto> consolidatedRows(List<AgedLoan> agedLoans) {
        BigDecimal portfolioTotal = sum(agedLoans, AgedLoan::totalOutstanding);
        List<AgingAnalysisConsolidatedRowDto> rows = new ArrayList<>();

        for (ConsolidatedBucket bucket : CONSOLIDATED_BUCKETS) {
            List<AgedLoan> bucketLoans = agedLoans.stream()
                .filter(loan -> loan.consolidatedBucket() == bucket)
                .toList();
            BigDecimal total = sum(bucketLoans, AgedLoan::totalOutstanding);
            rows.add(new AgingAnalysisConsolidatedRowDto(
                bucket.name(),
                bucket.label(),
                (long) bucketLoans.size(),
                sum(bucketLoans, AgedLoan::principalOutstanding),
                sum(bucketLoans, AgedLoan::interestOutstanding),
                total,
                percent(total, portfolioTotal)
            ));
        }

        return rows;
    }

    private List<AgingAnalysisConsolidatedRowDto> consolidatedRowsFromBranchRows(List<AgingAnalysisBranchRowDto> branchRows) {
        BigDecimal portfolioTotal = branchRows.stream()
            .map(AgingAnalysisBranchRowDto::total)
            .reduce(ZERO, BigDecimal::add);
        BigDecimal interestTotal = branchRows.stream()
            .map(AgingAnalysisBranchRowDto::interestOutstanding)
            .reduce(ZERO, BigDecimal::add);
        BigDecimal days61To120 = branchRows.stream()
            .map(row -> money(row.bucket61To90()).add(money(row.bucket91To120())))
            .reduce(ZERO, BigDecimal::add);

        return List.of(
            consolidatedRow("CURRENT", "Current", sumBranch(branchRows, AgingAnalysisBranchRowDto::current), portfolioTotal, interestTotal),
            consolidatedRow("DAYS_1_TO_30", "1--30", sumBranch(branchRows, AgingAnalysisBranchRowDto::bucket1To30), portfolioTotal, interestTotal),
            consolidatedRow("DAYS_31_TO_60", "31--60", sumBranch(branchRows, AgingAnalysisBranchRowDto::bucket31To60), portfolioTotal, interestTotal),
            consolidatedRow("DAYS_61_TO_120", "61--120", days61To120, portfolioTotal, interestTotal),
            consolidatedRow("DAYS_121_TO_150", "121--150", sumBranch(branchRows, AgingAnalysisBranchRowDto::bucket121To150), portfolioTotal, interestTotal),
            consolidatedRow("DAYS_151_TO_180", "151--180", sumBranch(branchRows, AgingAnalysisBranchRowDto::bucket151To180), portfolioTotal, interestTotal),
            consolidatedRow("ABOVE_180", "180 & Above", sumBranch(branchRows, AgingAnalysisBranchRowDto::bucketAbove180), portfolioTotal, interestTotal)
        );
    }

    private AgingAnalysisConsolidatedRowDto consolidatedRow(
        String bucket,
        String label,
        BigDecimal total,
        BigDecimal portfolioTotal,
        BigDecimal interestTotal
    ) {
        BigDecimal interest = proportional(total, interestTotal, portfolioTotal);
        BigDecimal principal = money(total).subtract(interest).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        return new AgingAnalysisConsolidatedRowDto(bucket, label, null, principal, interest, total, percent(total, portfolioTotal));
    }

    private AgingAnalysisSummaryDto summary(List<AgedLoan> agedLoans) {
        BigDecimal current = agedLoans.stream()
            .filter(loan -> loan.bucket() == AgingBucket.CURRENT)
            .map(AgedLoan::totalOutstanding)
            .reduce(ZERO, BigDecimal::add);
        BigDecimal total = sum(agedLoans, AgedLoan::totalOutstanding);
        return new AgingAnalysisSummaryDto(
            agedLoans.size(),
            total,
            money(current),
            money(total.subtract(current)),
            total
        );
    }

    private AgingAnalysisSummaryDto summaryFromBranchRows(List<AgingAnalysisBranchRowDto> rows) {
        BigDecimal current = sumBranch(rows, AgingAnalysisBranchRowDto::current);
        BigDecimal total = sumBranch(rows, AgingAnalysisBranchRowDto::total);
        return new AgingAnalysisSummaryDto(
            rows.stream().mapToLong(AgingAnalysisBranchRowDto::noOfAccounts).sum(),
            sumBranch(rows, AgingAnalysisBranchRowDto::aum),
            money(current),
            money(total.subtract(current)),
            total
        );
    }

    private BigDecimal sumBranch(List<AgingAnalysisBranchRowDto> rows, BranchMoneyGetter getter) {
        return money(rows.stream().map(getter::get).reduce(ZERO, BigDecimal::add));
    }

    private List<AgingAnalysisMatrixRowDto> matrixRows(List<AgedLoan> agedLoans, LabelGetter labelGetter, OrderGetter orderGetter) {
        Map<String, List<AgedLoan>> grouped = agedLoans.stream()
            .collect(Collectors.groupingBy(labelGetter::get, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
            .sorted(Comparator.comparing(entry -> orderGetter.get(entry.getValue().get(0))))
            .map(entry -> matrixRow(entry.getKey(), entry.getValue()))
            .toList();
    }

    private AgingAnalysisMatrixRowDto matrixRow(String label, List<AgedLoan> loans) {
        return new AgingAnalysisMatrixRowDto(
            label,
            crore(sum(loans, AgedLoan::principalOutstanding)),
            crore(sumByParBucket(loans, ParBucket.STANDARD)),
            crore(sumByParBucket(loans, ParBucket.DAYS_0_TO_30)),
            crore(sumByParBucket(loans, ParBucket.DAYS_31_TO_60)),
            crore(sumByParBucket(loans, ParBucket.DAYS_61_TO_90)),
            crore(sumByParBucket(loans, ParBucket.DAYS_91_TO_180)),
            crore(sumByParBucket(loans, ParBucket.DAYS_181_TO_365)),
            crore(sumByParBucket(loans, ParBucket.ABOVE_365))
        );
    }

    private BigDecimal sumByParBucket(List<AgedLoan> loans, ParBucket bucket) {
        return loans.stream()
            .filter(loan -> parBucket(loan) == bucket)
            .map(AgedLoan::principalOutstanding)
            .reduce(ZERO, BigDecimal::add);
    }

    private ParBucket parBucket(AgedLoan loan) {
        if (loan.ageDays() <= 0) return ParBucket.STANDARD;
        if (loan.ageDays() <= 30) return ParBucket.DAYS_0_TO_30;
        if (loan.ageDays() <= 60) return ParBucket.DAYS_31_TO_60;
        if (loan.ageDays() <= 90) return ParBucket.DAYS_61_TO_90;
        if (loan.ageDays() <= 180) return ParBucket.DAYS_91_TO_180;
        if (loan.ageDays() <= 365) return ParBucket.DAYS_181_TO_365;
        return ParBucket.ABOVE_365;
    }

    private String loanTicketLabel(AgedLoan loan) {
        BigDecimal lakh = loan.loanAmount().divide(BigDecimal.valueOf(100_000), 4, RoundingMode.HALF_UP);
        if (lakh.compareTo(BigDecimal.ONE) <= 0) return "<= 1";
        if (lakh.compareTo(BigDecimal.valueOf(2)) <= 0) return "> 1 - 2";
        if (lakh.compareTo(BigDecimal.valueOf(5)) <= 0) return "> 2 - 5";
        if (lakh.compareTo(BigDecimal.TEN) <= 0) return "> 5 - 10";
        return "> 10";
    }

    private int loanTicketOrder(AgedLoan loan) {
        return switch (loanTicketLabel(loan)) {
            case "<= 1" -> 1;
            case "> 1 - 2" -> 2;
            case "> 2 - 5" -> 3;
            case "> 5 - 10" -> 4;
            default -> 5;
        };
    }

    private String interestLabel(AgedLoan loan) {
        return money(loan.flatInterestRate()).stripTrailingZeros().toPlainString() + "%";
    }

    private int interestOrder(AgedLoan loan) {
        return money(loan.flatInterestRate()).multiply(BigDecimal.valueOf(100)).intValue();
    }

    private Map<AgingBucket, BigDecimal> totalsByBranchBucket(List<AgedLoan> loans) {
        Map<AgingBucket, BigDecimal> totals = new LinkedHashMap<>();
        for (AgedLoan loan : loans) {
            totals.merge(loan.bucket(), loan.totalOutstanding(), BigDecimal::add);
        }
        return totals;
    }

    private AgingBucket bucket(DemandListRowDto row, java.time.LocalDate asOnDate) {
        long ageDays = overdueAgeDays(row, asOnDate);
        if (ageDays <= 0) {
            return AgingBucket.CURRENT;
        }

        if (ageDays <= 30) return AgingBucket.DAYS_1_TO_30;
        if (ageDays <= 60) return AgingBucket.DAYS_31_TO_60;
        if (ageDays <= 90) return AgingBucket.DAYS_61_TO_90;
        if (ageDays <= 120) return AgingBucket.DAYS_91_TO_120;
        if (ageDays <= 150) return AgingBucket.DAYS_121_TO_150;
        if (ageDays <= 180) return AgingBucket.DAYS_151_TO_180;
        return AgingBucket.ABOVE_180;
    }

    private AgingBucket bucket(Integer overdueEmiCount) {
        int count = overdueEmiCount == null ? 0 : overdueEmiCount;
        if (count <= 0) return AgingBucket.CURRENT;
        if (count == 1) return AgingBucket.DAYS_1_TO_30;
        if (count == 2) return AgingBucket.DAYS_31_TO_60;
        if (count == 3) return AgingBucket.DAYS_61_TO_90;
        if (count == 4) return AgingBucket.DAYS_91_TO_120;
        if (count == 5) return AgingBucket.DAYS_121_TO_150;
        if (count == 6) return AgingBucket.DAYS_151_TO_180;
        return AgingBucket.ABOVE_180;
    }

    private long overdueAgeDays(DemandListRowDto row, LocalDate asOnDate) {
        if (money(row.overdueAmount()).compareTo(ZERO) <= 0 || row.overdueFromDate() == null) {
            return 0;
        }
        return Math.max(1, ChronoUnit.DAYS.between(row.overdueFromDate(), asOnDate));
    }

    private long overdueAgeDays(Integer overdueEmiCount, LocalDate overdueFromDate, LocalDate asOnDate) {
        int count = overdueEmiCount == null ? 0 : overdueEmiCount;
        if (count <= 0) {
            return 0;
        }
        if (overdueFromDate == null) {
            return Math.max(1, count * 30L);
        }
        return Math.max(1, ChronoUnit.DAYS.between(overdueFromDate, asOnDate));
    }

    private ConsolidatedBucket consolidatedBucket(AgingBucket bucket) {
        return switch (bucket) {
            case CURRENT -> ConsolidatedBucket.CURRENT;
            case DAYS_1_TO_30 -> ConsolidatedBucket.DAYS_1_TO_30;
            case DAYS_31_TO_60 -> ConsolidatedBucket.DAYS_31_TO_60;
            case DAYS_61_TO_90, DAYS_91_TO_120 -> ConsolidatedBucket.DAYS_61_TO_120;
            case DAYS_121_TO_150 -> ConsolidatedBucket.DAYS_121_TO_150;
            case DAYS_151_TO_180 -> ConsolidatedBucket.DAYS_151_TO_180;
            case ABOVE_180 -> ConsolidatedBucket.ABOVE_180;
        };
    }

    private AgingAnalysisRequest validate(AgingAnalysisRequest request) {
        if (request == null || request.asOnDate() == null) {
            throw new IllegalArgumentException("As On Date is required");
        }
        return new AgingAnalysisRequest(
            request.asOnDate(),
            cleanNullable(request.areaCode()),
            cleanNullable(request.contractNumber())
        );
    }

    private AgingAnalysisRequest validateBranchWiseRequest(AgingAnalysisRequest request) {
        if (request == null || request.asOnDate() == null) {
            throw new IllegalArgumentException("As On Date is required");
        }
        return new AgingAnalysisRequest(
            request.asOnDate(),
            cleanNullable(request.areaCode()),
            cleanNullable(request.contractNumber())
        );
    }

    private List<String> warnings(List<AgedLoan> agedLoans) {
        return agedLoans.stream()
            .map(AgedLoan::warning)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
    }

    private BigDecimal sum(List<AgedLoan> loans, MoneyGetter getter) {
        return money(loans.stream().map(getter::get).reduce(ZERO, BigDecimal::add));
    }

    private BigDecimal percent(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return money(value).multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal crore(BigDecimal value) {
        return money(value).divide(BigDecimal.valueOf(10_000_000), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal proportional(BigDecimal value, BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return money(value).multiply(money(numerator)).divide(money(denominator), 2, RoundingMode.HALF_UP);
    }

    private int frequencyMonths(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (normalized.contains("quarter")) return 3;
        if (normalized.contains("half") || normalized.contains("semi")) return 6;
        if (normalized.contains("year") || normalized.contains("annual")) return 12;
        return 1;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String clean(String value) {
        String cleaned = cleanNullable(value);
        return cleaned == null ? "Unassigned" : cleaned;
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String cleanPortfolioArea(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
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

    private enum AgingBucket {
        CURRENT,
        DAYS_1_TO_30,
        DAYS_31_TO_60,
        DAYS_61_TO_90,
        DAYS_91_TO_120,
        DAYS_121_TO_150,
        DAYS_151_TO_180,
        ABOVE_180
    }

    private enum ConsolidatedBucket {
        CURRENT("Current"),
        DAYS_1_TO_30("1--30"),
        DAYS_31_TO_60("31--60"),
        DAYS_61_TO_120("61--120"),
        DAYS_121_TO_150("121--150"),
        DAYS_151_TO_180("151--180"),
        ABOVE_180("180 & Above");

        private final String label;

        ConsolidatedBucket(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private record AgedLoan(
        Long contractId,
        String area,
        AgingBucket bucket,
        ConsolidatedBucket consolidatedBucket,
        BigDecimal loanAmount,
        BigDecimal flatInterestRate,
        BigDecimal totalOutstanding,
        BigDecimal principalOutstanding,
        BigDecimal interestOutstanding,
        long ageDays,
        String warning
    ) {
    }

    private enum ParBucket {
        STANDARD,
        DAYS_0_TO_30,
        DAYS_31_TO_60,
        DAYS_61_TO_90,
        DAYS_91_TO_180,
        DAYS_181_TO_365,
        ABOVE_365
    }

    private record ScheduleItem(LocalDate dueDate, BigDecimal amount) {
    }

    @FunctionalInterface
    private interface MoneyGetter {
        BigDecimal get(AgedLoan loan);
    }

    @FunctionalInterface
    private interface BranchMoneyGetter {
        BigDecimal get(AgingAnalysisBranchRowDto row);
    }

    @FunctionalInterface
    private interface LabelGetter {
        String get(AgedLoan loan);
    }

    @FunctionalInterface
    private interface OrderGetter {
        int get(AgedLoan loan);
    }

    private static final class DemandListServiceDefaults {
        private static final int PRINT_ROW_LIMIT = 10_000;
    }
}
