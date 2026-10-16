package dugar_lms_api.modules.reports.stockstatement;

import dugar_lms_api.modules.contracts.repository.StockBankMarkingRepository;
import dugar_lms_api.modules.contracts.repository.ContractDocumentationDraftRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;

/** Explicit local-only acceptance check. Every test write is rolled back. */
@EnabledIfSystemProperty(named = "stock.bank.local.integration", matches = "true")
class StockBankLocalIntegrationTest {
    private static String resolve(String value) {
        if (value.startsWith("${")) {
            String[] parts = value.substring(2, value.length() - 1).split(":", 2);
            return System.getenv().getOrDefault(parts[0], parts[1]);
        }
        return value;
    }
    @Test void migratedAssignmentsSaveReadBackAndHistoricalReportUseSameBankId() throws Exception {
        var properties = new Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) { properties.load(in); }
        String url = resolve(properties.getProperty("spring.datasource.url"));
        assertThat(url).isEqualTo("jdbc:postgresql://127.0.0.1:5434/dugar_lms");
        var dataSource = new DriverManagerDataSource(url, resolve(properties.getProperty("spring.datasource.username")), resolve(properties.getProperty("spring.datasource.password")));
        var jdbc = new JdbcTemplate(dataSource);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='contract_details' AND column_name='stock_marked_to_bank'", String.class)).isEqualTo("character varying");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_bank_mapping_backup old JOIN contract_details cd USING(contract_detail_id) WHERE NULLIF(BTRIM(old.stock_marked_to_bank),'') IS NOT NULL AND cd.stock_marked_to_bank='1'", Integer.class)).isEqualTo(51);
            if (Boolean.getBoolean("stock.bank.require.source.retired")) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='contract_details' AND column_name='stock_marked_bank_id'", Integer.class)).isZero();
            }
            var marking = new StockBankMarkingRepository(jdbc);
            var documentation = new ContractDocumentationDraftRepository(new NamedParameterJdbcTemplate(jdbc));
            var reports = new StockStatementService(new StockStatementRepository(jdbc));
            var scope = new ReportAccessScope(true, 1L, "admin");
            var date = LocalDate.of(2026, 9, 30);
            var preview = reports.generate(1L, date, scope);
            assertThat(preview.businessDate()).isEqualTo(date);
            assertThat(preview.rows()).isNotEmpty();
            Long contractId = preview.rows().getFirst().contractId();
            String originalBankName = jdbc.queryForObject("SELECT bank_name FROM bank_masters WHERE id=1", String.class);
            jdbc.update("UPDATE bank_masters SET bank_name=? WHERE id=1", originalBankName + " (rename test)");
            assertThat(marking.byId(contractId).bankName()).isEqualTo(originalBankName + " (rename test)");
            assertThat(reports.generate(1L, date, scope).rows()).extracting("contractId").contains(contractId);
            jdbc.update("UPDATE bank_masters SET bank_name=? WHERE id=1", originalBankName);
            var current = marking.byId(contractId);
            assertThat(marking.byNumber(current.contractNumber())).extracting("contractId").contains(contractId);
            assertThat(documentation.findByContractId(contractId).orElseThrow().stockMarkedToBank()).isEqualTo(1L);
            marking.lockActiveContract(contractId);
            marking.validateBank(10L, current.bankId());
            marking.save(contractId, 10L, "stock-bank-integration-test");
            assertThat(marking.byId(contractId).bankId()).isEqualTo(10L);
            assertThat(jdbc.queryForObject("SELECT stock_marked_to_bank FROM contract_details WHERE contract_id=?", String.class, contractId)).isEqualTo("10");
            assertThat(documentation.findByContractId(contractId).orElseThrow().stockMarkedToBank()).isEqualTo(10L);
            assertThat(reports.generate(1L, date, scope).rows()).extracting("contractId").doesNotContain(contractId);
            assertThat(reports.generate(10L, date, scope).rows()).extracting("contractId").contains(contractId);
            // Verify the reverse path using the existing Contract Edit repository.
            var doc = documentation.findByContractId(contractId).orElseThrow();
            var reset = new dugar_lms_api.modules.contracts.dto.ContractDocumentationDraftDto(
                doc.contractId(), doc.documentType(), doc.documentVerifiedBy(), doc.documentsObtainedBy(),
                doc.guarantorFiBy(), doc.borrowerFiBy(), doc.loanReferredBy(), doc.tvrDoneBy(), doc.vehicleByAgency(),
                doc.vehicleInspectionBy(), doc.propertyValuationBy(), doc.legalOpinionBy(), doc.branchCollectionToolBy(),
                doc.documentsCheckedBy(), doc.documentsVerifiedBy(), doc.loanApprovedBy(), doc.disbursedBy(),
                doc.rcOnlineChecking(), doc.hoCollectionToolBy(), doc.areaCode(), doc.hoTvrDoneBy(), 1L,
                doc.stockMarkedBankName(), doc.pendingDocuments(), doc.documentUploads());
            documentation.upsertContractDetail(reset, "stock-bank-integration-test");
            assertThat(marking.byId(contractId).bankId()).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT stock_marked_to_bank FROM contract_details WHERE contract_id=?", String.class, contractId)).isEqualTo("1");
            // An inactive contract cannot be searched or marked, even if status changes after selection.
            jdbc.update("UPDATE contracts SET is_active = FALSE WHERE contract_id = ?", contractId);
            assertThat(marking.byNumber(current.contractNumber())).isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> marking.lockActiveContract(contractId));
        });
    }
}
