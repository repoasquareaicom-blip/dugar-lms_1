package dugar_lms_api.modules.wordtemplates;

import dugar_lms_api.modules.wordtemplates.automation.HindiFormats;
import dugar_lms_api.modules.wordtemplates.automation.ReminderLetterService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="DOCUMENT_MENU_TEST_URL", matches=".+")
class ReminderLetterFinancialMigrationTest {
    @TempDir Path temp;
    ReminderLetterFunctionMigrationTest fixture;
    JdbcTemplate jdbc;
    final Path migration=Path.of("src/main/resources/db/migration/V083__correct_reminder_overdue_and_partial_emi.sql");

    @BeforeEach void setup() throws Exception {
        // Reuse the guarded disposable Flyway baseline; never target an application DB.
        fixture=new ReminderLetterFunctionMigrationTest();fixture.temp=temp;fixture.fixture();
        Files.copy(fixture.migration,temp.resolve(fixture.migration.getFileName()));
        fixture.flyway.migrate();
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(DriverManager.getConnection(fixture.url,"postgres",""),true));
    }
    @AfterEach void close() {
        if(jdbc!=null)((SingleConnectionDataSource)Objects.requireNonNull(jdbc.getDataSource())).destroy();
    }
    private void migrate() throws Exception {
        Files.copy(migration,temp.resolve(migration.getFileName()));
        assertThat(fixture.flyway.migrate().migrationsExecuted).isEqualTo(1);
        fixture.flyway.validate();
        assertThat(fixture.flyway.migrate().migrationsExecuted).isZero();
    }
    @Test void flywayCorrectionPreservesOidDependentsOwnerAclCommentAndThirteenFields() throws Exception {
        jdbc.execute("CREATE VIEW reminder_dependent AS SELECT * FROM public.fn_doc_reminder_letter(41,'2026-10-24')");
        jdbc.execute("COMMENT ON FUNCTION public.fn_doc_reminder_letter(bigint,date) IS 'Retain reminder comment'; REVOKE ALL ON FUNCTION public.fn_doc_reminder_letter(bigint,date) FROM PUBLIC; GRANT EXECUTE ON FUNCTION public.fn_doc_reminder_letter(bigint,date) TO pg_monitor WITH GRANT OPTION; ALTER FUNCTION public.fn_doc_reminder_letter(bigint,date) OWNER TO pg_read_all_data");
        String metadata="SELECT oid,proowner,proacl::text,obj_description(oid,'pg_proc') comment FROM pg_proc WHERE oid='public.fn_doc_reminder_letter(bigint,date)'::regprocedure";
        var before=jdbc.queryForMap(metadata);
        migrate();
        assertThat(jdbc.queryForMap(metadata)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT to_regclass('reminder_dependent') IS NOT NULL",Boolean.class)).isTrue();
        jdbc.execute("CREATE TEMP TABLE tmp_contract_report(contract_id BIGINT,current_due NUMERIC,overdue_emi_count INTEGER,total_outstanding NUMERIC,overdue_amount NUMERIC)");
        jdbc.execute("CREATE TEMP TABLE tmp_emi_outstanding(contract_id BIGINT,emi_no INTEGER,emi_due_date DATE,installment_amount NUMERIC,emi_outstanding NUMERIC)");
        jdbc.execute("INSERT INTO tmp_contract_report VALUES(41,123.45,2,1000,200.25)");
        jdbc.execute("INSERT INTO tmp_emi_outstanding VALUES(41,1,'2026-08-17',123.45,0),(41,2,'2026-09-17',123.45,76.80),(41,3,'2026-10-17',123.45,123.45),(41,4,'2026-11-17',123.45,123.45)");
        var result=jdbc.queryForMap("SELECT * FROM public.fn_doc_reminder_letter(41,'2026-10-24')");
        assertThat(result).hasSize(13);
        assertThat((java.math.BigDecimal)result.get("CURRENT_DUE")).isEqualByComparingTo("76.80");
        assertThat((java.math.BigDecimal)result.get("TOTAL_OS")).isEqualByComparingTo("200.25");
        assertThat((java.math.BigDecimal)result.get("EMI")).isEqualByComparingTo("123.45");
        assertThat(result).containsEntry("NO_OF_OVERDUES",2).containsEntry("CONTRACT_NUMBER","CN/2026-0041");
    }
    @Test void customFinancialBodyAbortsWithoutOverwritingIt() throws Exception {
        String definition=jdbc.queryForObject("SELECT pg_get_functiondef('public.fn_doc_reminder_letter(bigint,date)'::regprocedure)",String.class);
        jdbc.execute(definition.replace("ROUND(COALESCE(r.current_due, 0), 2)","ROUND(COALESCE(r.current_due, 0), 3)"));
        Files.copy(migration,temp.resolve(migration.getFileName()));
        assertThatThrownBy(()->fixture.flyway.migrate()).hasMessageContaining("differs from the V078 baseline");
        assertThat(jdbc.queryForObject("SELECT prosrc FROM pg_proc WHERE oid='public.fn_doc_reminder_letter(bigint,date)'::regprocedure",String.class)).contains("ROUND(COALESCE(r.current_due, 0), 3)");
    }
    @Test void javaPreservesCorrectedSqlValuesAndHindiWordsUseTheSameOverdueAmount() {
        var values=ReminderLetterService.values("""
            {"BORROWER_NAME":"Borrower","BORROWER_SWD":"Parent","VEHICLE":"Vehicle","EMI":"26000.00",
             "CURRENT_DUE":"12000.00","NO_OF_OVERDUES":"9","TOTAL_OS":"220000.00",
             "TOTAL_OS_WORDS":"Two Lakh Twenty Thousand Rupees Only","GUARANTOR_1_NAME":"G1",
             "GUARANTOR_2_NAME":"G2","BORROWER_ADDRESS":"Address","CONTRACT_NUMBER":"12755","CONTRACT_DATE":"16 Apr 2023"}
            """);
        assertThat(values).hasSize(13).containsEntry("{{EMI}}","26000.00")
            .containsEntry("{{CURRENT_DUE}}","12000.00").containsEntry("{{TOTAL_OS}}","220000.00")
            .containsEntry("{{TOTAL_OS_WORDS}}","Two Lakh Twenty Thousand Rupees Only");
        assertThat(HindiFormats.format("AMOUNT_WORDS",values.get("{{TOTAL_OS}}")))
            .isEqualTo("दो लाख बीस हज़ार रुपये मात्र");
    }
}
