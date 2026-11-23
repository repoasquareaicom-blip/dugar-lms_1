package dugar_lms_api.modules.wordtemplates;

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
class ReminderLetterCompatibilityMigrationTest {
    @TempDir Path temp;
    ReminderLetterFunctionMigrationTest fixture;
    JdbcTemplate jdbc;
    final Path migrations=Path.of("src/main/resources/db/migration");
    final String correction="V083__correct_reminder_overdue_and_partial_emi.sql";
    final String compatibility="V084__validate_reminder_financial_mapping_compatibility.sql";
    String v078;

    @BeforeEach void setup() throws Exception {
        fixture=new ReminderLetterFunctionMigrationTest();fixture.temp=temp;fixture.fixture();
        Files.copy(fixture.migration,temp.resolve(fixture.migration.getFileName()));
        fixture.flyway.migrate();
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(DriverManager.getConnection(fixture.url,"postgres",""),true));
        v078=definition();
        Files.copy(migrations.resolve(correction),temp.resolve(correction));
        fixture.flyway.migrate();
    }
    @AfterEach void close() {
        if(jdbc!=null)((SingleConnectionDataSource)Objects.requireNonNull(jdbc.getDataSource())).destroy();
    }
    private String definition() {
        return jdbc.queryForObject("SELECT pg_get_functiondef('public.fn_doc_reminder_letter(bigint,date)'::regprocedure)",String.class);
    }
    private Map<String,Object> metadata() {
        return jdbc.queryForMap("SELECT oid,proowner,proacl::text,pg_get_function_result(oid) outputs,obj_description(oid,'pg_proc') comment FROM pg_proc WHERE oid='public.fn_doc_reminder_letter(bigint,date)'::regprocedure");
    }
    private void migrate() throws Exception {
        Files.copy(migrations.resolve(compatibility),temp.resolve(compatibility));
        assertThat(fixture.flyway.migrate().migrationsExecuted).isEqualTo(1);
        fixture.flyway.validate();
        assertThat(fixture.flyway.migrate().migrationsExecuted).isZero();
    }
    private void rows() {
        jdbc.execute("CREATE TEMP TABLE tmp_contract_report(contract_id BIGINT,current_due NUMERIC,overdue_emi_count INTEGER,total_outstanding NUMERIC,overdue_amount NUMERIC)");
        jdbc.execute("CREATE TEMP TABLE tmp_emi_outstanding(contract_id BIGINT,emi_no INTEGER,emi_due_date DATE,installment_amount NUMERIC,emi_outstanding NUMERIC)");
        jdbc.execute("INSERT INTO tmp_contract_report VALUES(41,123.45,2,1000,200.25)");
        jdbc.execute("INSERT INTO tmp_emi_outstanding VALUES(41,1,'2026-08-17',123.45,0),(41,2,'2026-09-17',123.45,76.80),(41,3,'2026-10-17',123.45,123.45),(41,4,'2026-11-17',123.45,123.45)");
    }
    private Map<String,Object> letter() {
        return jdbc.queryForMap("SELECT * FROM public.fn_doc_reminder_letter(41,'2026-10-24')");
    }
    @Test void reproducesV083ReplayFailureAndForwardMigrationAcceptsAlreadyCorrectedFunction() throws Exception {
        String before=definition();var beforeMetadata=metadata();rows();var beforeValues=letter();
        assertThatThrownBy(()->jdbc.execute(Files.readString(migrations.resolve(correction))))
            .hasMessageContaining("differs from the V078 baseline");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='083' AND success",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success",Integer.class)).isZero();
        migrate();
        jdbc.execute(Files.readString(migrations.resolve(compatibility))); // Safe SQL replay as well as Flyway repeat.
        assertThat(definition()).isEqualTo(before);
        assertThat(metadata()).isEqualTo(beforeMetadata);
        assertThat(letter()).isEqualTo(beforeValues);
    }
    @Test void knownV078BodyIsCorrectedWithoutLosingAnyPlaceholderOrDependentObject() throws Exception {
        jdbc.execute(v078); // Model a known baseline restored after a successful V083.
        jdbc.execute("CREATE VIEW reminder_dependent AS SELECT * FROM public.fn_doc_reminder_letter(41,'2026-10-24')");
        jdbc.execute("COMMENT ON FUNCTION public.fn_doc_reminder_letter(bigint,date) IS 'Retain metadata'; REVOKE ALL ON FUNCTION public.fn_doc_reminder_letter(bigint,date) FROM PUBLIC; GRANT EXECUTE ON FUNCTION public.fn_doc_reminder_letter(bigint,date) TO pg_monitor WITH GRANT OPTION; ALTER FUNCTION public.fn_doc_reminder_letter(bigint,date) OWNER TO pg_read_all_data");
        rows();var before=letter();var beforeMetadata=metadata();
        migrate();var after=letter();
        assertThat(metadata()).isEqualTo(beforeMetadata);
        assertThat(jdbc.queryForObject("SELECT to_regclass('reminder_dependent') IS NOT NULL",Boolean.class)).isTrue();
        assertThat(after).hasSize(13).containsKeys("CONTRACT_NUMBER","CONTRACT_DATE","BORROWER_ADDRESS");
        for(String key:before.keySet()) {
            if(!Set.of("CURRENT_DUE","TOTAL_OS","TOTAL_OS_WORDS").contains(key))
                assertThat(after.get(key)).as(key).isEqualTo(before.get(key));
        }
        assertThat((java.math.BigDecimal)after.get("CURRENT_DUE")).isEqualByComparingTo("76.80");
        assertThat((java.math.BigDecimal)after.get("TOTAL_OS")).isEqualByComparingTo("200.25");
    }
    @Test void lineEndingsAndOuterWhitespaceAreCompatible() throws Exception {
        String before=definition();String body=jdbc.queryForObject("SELECT prosrc FROM pg_proc WHERE oid='public.fn_doc_reminder_letter(bigint,date)'::regprocedure",String.class);
        String formatted=before.replace(body," \t\r\n"+body.replace("\r","").replace("\n","\r\n")+"\r\n \t");
        jdbc.execute(formatted);String stored=definition();
        migrate();assertThat(definition()).isEqualTo(stored);
    }
    @Test void unknownFinancialCustomizationIsRejectedWithoutReplacingTheFunction() throws Exception {
        jdbc.execute(definition().replace("ROUND(COALESCE(r.overdue_amount, 0), 2)","ROUND(COALESCE(r.overdue_amount, 0), 3)"));
        String before=definition();var beforeMetadata=metadata();
        Files.copy(migrations.resolve(compatibility),temp.resolve(compatibility));
        assertThatThrownBy(()->fixture.flyway.migrate()).hasMessageContaining("neither approved V078 nor approved V083");
        assertThat(definition()).isEqualTo(before);assertThat(metadata()).isEqualTo(beforeMetadata);
    }
    @Test void unexpectedOutputStructureIsRejected() throws Exception {
        String changed=definition().replace("\"CONTRACT_DATE\" text","\"UNEXPECTED_DATE\" text");
        jdbc.execute("DROP FUNCTION public.fn_doc_reminder_letter(bigint,date) RESTRICT");
        jdbc.execute(changed);String before=definition();
        Files.copy(migrations.resolve(compatibility),temp.resolve(compatibility));
        assertThatThrownBy(()->fixture.flyway.migrate()).hasMessageContaining("unexpected input/output signature");
        assertThat(definition()).isEqualTo(before);
    }
}
