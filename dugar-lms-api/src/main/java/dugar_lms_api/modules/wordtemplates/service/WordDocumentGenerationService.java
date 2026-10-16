package dugar_lms_api.modules.wordtemplates.service;

import dugar_lms_api.modules.reports.ReportAccessScope;
import dugar_lms_api.modules.wordtemplates.dto.GenerateWordDocumentRequest;
import dugar_lms_api.modules.wordtemplates.dto.WordContractOptionDto;
import dugar_lms_api.modules.wordtemplates.dto.WordPlaceholderFieldDto;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateValidationDto;
import dugar_lms_api.modules.wordtemplates.model.WordTemplateFile;
import dugar_lms_api.modules.wordtemplates.repository.WordDocumentGenerationRepository;
import dugar_lms_api.modules.wordtemplates.repository.WordTemplateRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class WordDocumentGenerationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter CONTRACT_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("##,##,##,##0.00");

    private final WordDocumentGenerationRepository repository;
    private final WordTemplateRepository templateRepository;
    private final WordTemplateStorageService storageService;
    private final WordPlaceholderRegistry placeholderRegistry;
    private final WordDocxTemplateEngine docxTemplateEngine;

    public WordDocumentGenerationService(
        WordDocumentGenerationRepository repository,
        WordTemplateRepository templateRepository,
        WordTemplateStorageService storageService,
        WordPlaceholderRegistry placeholderRegistry,
        WordDocxTemplateEngine docxTemplateEngine
    ) {
        this.repository = repository;
        this.templateRepository = templateRepository;
        this.storageService = storageService;
        this.placeholderRegistry = placeholderRegistry;
        this.docxTemplateEngine = docxTemplateEngine;
    }

    public List<WordPlaceholderFieldDto> placeholders(String dataSource) {
        return placeholderRegistry.fields(normalizeDataSource(dataSource));
    }

    public WordTemplateValidationDto validate(Long templateId, String dataSource) {
        WordTemplateFile file = requireTemplateFile(templateId);
        return validation(storageService.path(file.storagePath()), normalizeDataSource(dataSource));
    }

    public List<WordContractOptionDto> searchContracts(String keyword, int limit, Authentication authentication) {
        return repository.searchContracts(keyword, limit, ReportAccessScope.from(authentication));
    }

    public GeneratedWordDocument generate(GenerateWordDocumentRequest request, Authentication authentication) {
        String dataSource = normalizeDataSource(request.dataSource());
        if (!WordPlaceholderRegistry.CONTRACT.equals(dataSource)) {
            throw new IllegalArgumentException("Only CONTRACT document generation is available.");
        }

        WordTemplateFile templateFile = requireTemplateFile(request.templateId());
        Path templatePath = storageService.path(templateFile.storagePath());
        WordTemplateValidationDto validation = validation(templatePath, dataSource);
        if (!validation.unknown().isEmpty()) {
            throw new IllegalArgumentException("Template contains unknown placeholders: " + String.join(", ", validation.unknown()));
        }

        Map<String, Object> contract = repository.findAccessibleContract(request.contractId(), ReportAccessScope.from(authentication))
            .orElseThrow(() -> new AccessDeniedException("Contract not found or not accessible."));
        Map<String, String> values = resolveContractValues(contract);
        List<String> unresolved = validation.recognized().stream()
            .filter(code -> !values.containsKey(code))
            .toList();
        if (!unresolved.isEmpty()) {
            throw new IllegalArgumentException("Template contains placeholders that cannot be resolved: " + String.join(", ", unresolved));
        }

        try {
            Path output = Files.createTempFile("generated-contract-", ".docx");
            docxTemplateEngine.merge(templatePath, output, values);
            String filename = filename(contract, templateFile.originalFilename());
            return new GeneratedWordDocument(filename, new UrlResource(output.toUri()));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to create generated Word document.");
        }
    }

    public WordTemplateValidationDto validation(Path path, String dataSource) {
        Set<String> known = placeholderRegistry.knownCodes(dataSource);
        List<String> placeholders = docxTemplateEngine.extractPlaceholders(path).stream().toList();
        List<String> recognized = placeholders.stream().filter(known::contains).toList();
        List<String> unknown = placeholders.stream().filter(code -> !known.contains(code)).toList();
        return new WordTemplateValidationDto(placeholders, recognized, unknown);
    }

    private Map<String, String> resolveContractValues(Map<String, Object> contract) {
        Long contractId = longValue(contract.get("contract_id"));
        Map<String, Map<String, Object>> sourceRows = new HashMap<>();
        sourceRows.put("CONTRACT", contract);
        sourceRows.put("BORROWER", repository.findParty(stringValue(contract.get("borrower_code")), "BORROWER").orElse(Map.of()));
        sourceRows.put("CO_APPLICANT", repository.findParty(stringValue(contract.get("co_applicant_code")), "CO_APPLICANT").orElse(Map.of()));
        sourceRows.put("GUARANTOR_1", repository.findParty(stringValue(contract.get("guarantor_code")), "GUARANTOR").orElse(Map.of()));
        sourceRows.put("GUARANTOR_2", repository.findParty(stringValue(contract.get("guarantor_2_code")), "GUARANTOR").orElse(Map.of()));
        sourceRows.put("BRANCH", repository.findBranch(stringValue(contract.get("area_code"))).orElse(Map.of()));
        sourceRows.put("DETAILS", repository.findActiveByContractId("contract_details", contractId, "contract_detail_id").orElse(Map.of()));
        sourceRows.put("FINANCIAL", repository.findActiveByContractId("contract_financial_terms", contractId, "contract_financial_term_id").orElse(Map.of()));

        List<Map<String, Object>> assets = repository.findRowsByContractId("assets", contractId, "asset_id", 1);
        Map<String, Object> asset = assets.isEmpty() ? Map.of() : assets.get(0);
        sourceRows.put("ASSET", asset);
        sourceRows.put("INSURANCE", repository.findInsurance(longValue(asset.get("asset_id"))).orElse(Map.of()));

        List<Map<String, Object>> repayments = repository.findRowsByContractId("contract_repayment_structures", contractId, "sequence_no", 3);
        for (int index = 0; index < 3; index++) {
            sourceRows.put("REPAYMENT_" + (index + 1), index < repayments.size() ? repayments.get(index) : Map.of());
        }
        List<Map<String, Object>> pendingDocuments = repository.findRowsByContractId("contract_pending_documents", contractId, "pending_document_id", 3);
        for (int index = 0; index < 3; index++) {
            sourceRows.put("PENDING_DOCUMENT_" + (index + 1), index < pendingDocuments.size() ? pendingDocuments.get(index) : Map.of());
        }

        Map<String, String> values = new HashMap<>();
        for (WordPlaceholderFieldDto field : placeholderRegistry.fields(WordPlaceholderRegistry.CONTRACT)) {
            if (field.calculated()) {
                values.put(field.placeholderCode(), calculatedValue(field.placeholderCode(), contract, repayments));
                continue;
            }
            String prefix = field.placeholderCode().substring(2, field.placeholderCode().length() - 2);
            String sourcePrefix = prefix.substring(0, prefix.length() - normalizeCode(field.sourceColumn()).length() - 1);
            Map<String, Object> row = sourceRows.getOrDefault(sourcePrefix, Map.of());
            if ("{{CONTRACT_CONTRACT_DATE}}".equals(field.placeholderCode())) {
                values.put(field.placeholderCode(), formatContractDate(row.get(field.sourceColumn())));
                continue;
            }
            if ("{{BORROWER_STATE}}".equals(field.placeholderCode())) {
                Object stateCode = row.get(field.sourceColumn());
                values.put(field.placeholderCode(), repository.findOtherDescription(stringValue(stateCode)).orElse(formatValue(stateCode)));
                continue;
            }
            values.put(field.placeholderCode(), formatValue(row.get(field.sourceColumn())));
        }
        return values;
    }

    private String calculatedValue(String code, Map<String, Object> contract, List<Map<String, Object>> repayments) {
        return switch (code) {
            case "{{LOAN_AMOUNT_IN_WORDS}}" -> amountInWords(bigDecimalValue(contract.get("loan_amount")));
            case "{{FORMATTED_CONTRACT_DATE}}" -> formatValue(contract.get("contract_date"));
            case "{{LOAN_TENURE}}" -> formatValue(contract.get("tenure_months"));
            case "{{EMI_AMOUNT}}" -> repayments.isEmpty() ? "" : formatValue(repayments.get(0).get("installment_amount"));
            case "{{REPAYMENT_SUMMARY}}" -> repaymentSummary(repayments);
            default -> "";
        };
    }

    private String repaymentSummary(List<Map<String, Object>> repayments) {
        return repayments.stream()
            .map(row -> "Seq " + formatValue(row.get("sequence_no")) + ": " + formatValue(row.get("number_of_installments")) + " x " + formatValue(row.get("installment_amount")))
            .toList()
            .stream()
            .reduce((left, right) -> left + "; " + right)
            .orElse("");
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDate date) {
            return DATE_FORMAT.format(date);
        }
        if (value instanceof BigDecimal decimal) {
            return AMOUNT_FORMAT.format(decimal);
        }
        return String.valueOf(value).trim();
    }

    private String formatContractDate(Object value) {
        LocalDate date = localDateValue(value);
        if (date == null) {
            return formatValue(value);
        }
        return date.getDayOfMonth() + ordinalSuffix(date.getDayOfMonth()) + " " + CONTRACT_DATE_FORMAT.format(date);
    }

    private String ordinalSuffix(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private LocalDate localDateValue(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return null;
    }

    private String amountInWords(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        long rupees = amount.setScale(0, RoundingMode.DOWN).longValue();
        int paise = amount.remainder(BigDecimal.ONE).movePointRight(2).abs().setScale(0, RoundingMode.HALF_UP).intValue();
        String words = indianNumberToWords(rupees) + " Rupees";
        if (paise > 0) {
            words += " and " + indianNumberToWords(paise) + " Paise";
        }
        return words + " Only";
    }

    private String indianNumberToWords(long number) {
        if (number == 0) {
            return "Zero";
        }
        String[] units = {"", "Thousand", "Lakh", "Crore"};
        long[] divisors = {1000, 100, 100, 100};
        StringBuilder result = new StringBuilder();
        int unitIndex = 0;
        long current = number;
        while (current > 0 && unitIndex < units.length) {
            long part = current % divisors[unitIndex];
            if (part > 0) {
                result.insert(0, belowThousandToWords((int) part) + (units[unitIndex].isBlank() ? "" : " " + units[unitIndex]) + " ");
            }
            current /= divisors[unitIndex];
            unitIndex++;
        }
        if (current > 0) {
            result.insert(0, belowThousandToWords((int) current) + " Crore ");
        }
        return result.toString().trim();
    }

    private String belowThousandToWords(int number) {
        String[] belowTwenty = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        if (number < 20) {
            return belowTwenty[number];
        }
        if (number < 100) {
            return (tens[number / 10] + " " + belowTwenty[number % 10]).trim();
        }
        return (belowTwenty[number / 100] + " Hundred " + belowThousandToWords(number % 100)).trim();
    }

    private WordTemplateFile requireTemplateFile(Long id) {
        return templateRepository.findTemplateFile(id)
            .orElseThrow(() -> new IllegalArgumentException("Word Template not found."));
    }

    private String normalizeDataSource(String dataSource) {
        return dataSource == null || dataSource.isBlank() ? WordPlaceholderRegistry.CONTRACT : dataSource.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private BigDecimal bigDecimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return null;
    }

    private String filename(Map<String, Object> contract, String originalFilename) {
        String contractNumber = String.valueOf(contract.getOrDefault("contract_number", "contract")).replaceAll("[^A-Za-z0-9._-]+", "_");
        String templateName = originalFilename == null || originalFilename.isBlank() ? "template.docx" : originalFilename;
        return contractNumber + "-" + templateName;
    }

    public record GeneratedWordDocument(String filename, Resource resource) {
    }
}
