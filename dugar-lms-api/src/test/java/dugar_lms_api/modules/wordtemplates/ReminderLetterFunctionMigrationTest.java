package dugar_lms_api.modules.wordtemplates;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="DOCUMENT_MENU_TEST_URL",matches=".+")
class ReminderLetterFunctionMigrationTest {
    @TempDir Path temp;
    Flyway flyway;
    String url;
    final Path migration=Path.of("src/main/resources/db/migration/V078__reminder_letter_contract_placeholders.sql");
    @BeforeEach void fixture() throws Exception {
        url=System.getenv("DOCUMENT_MENU_TEST_URL");
        if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/document_menu_test"))throw new IllegalArgumentException("Disposable local DB required");
        String original=Files.readString(Path.of("docs/reminder-letter-function-before-v078.sql"));
        Files.writeString(temp.resolve("V077__fixture.sql"),"""
            CREATE TABLE contracts(contract_id BIGINT PRIMARY KEY,contract_number TEXT,contract_date DATE,
                borrower_code TEXT,vehicle_make TEXT,guarantor_code TEXT,guarantor_2_code TEXT,first_emi_date DATE,
                is_active BOOLEAN,is_draft BOOLEAN,status TEXT,loan_close_date DATE);
            CREATE TABLE party_masters(party_code TEXT,full_name TEXT,salutation TEXT,swd_name TEXT,
                address_line_1 TEXT,address_line_2 TEXT,area TEXT,city TEXT,state TEXT,pin_code TEXT);
            CREATE TABLE g_others(other_code TEXT,other_desc TEXT);
            CREATE TABLE assets(asset_id BIGINT,contract_id BIGINT,vehicle_make TEXT,version TEXT);
            CREATE TABLE contract_repayment_structures(contract_id BIGINT,sequence_no INTEGER,number_of_installments INTEGER,installment_amount NUMERIC);
            CREATE FUNCTION public.fn_doc_inr_words(amount NUMERIC) RETURNS TEXT LANGUAGE sql AS 'SELECT ''existing SQL words''::text';
            INSERT INTO contracts VALUES(41,'CN/2026-0041','2026-10-17','B1','Fallback','G1','G2','2026-09-17',true,false,'Y',NULL);
            INSERT INTO contracts VALUES(42,NULL,NULL,'B1','Fallback','G1','G2','2026-09-17',true,false,'Y',NULL);
            INSERT INTO party_masters VALUES('B1','Borrower','Mr','Parent','Line 1.','Line 2','Area','City','ST','123456'),
                ('G1','Guarantor 1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),('G2','Guarantor 2',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL);
            INSERT INTO g_others VALUES('ST','State');
            INSERT INTO assets VALUES(2,41,'Later','V2'),(1,41,'First','V1');
            INSERT INTO contract_repayment_structures VALUES(41,1,12,123.45);
            """+original);
        flyway=Flyway.configure().dataSource(url,"postgres","").locations("filesystem:"+temp).cleanDisabled(false).load();
        flyway.clean();flyway.migrate();
    }
    private void migrate() throws Exception {
        Files.copy(migration,temp.resolve(migration.getFileName()));
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
    private JdbcTemplate connection() throws Exception {
        return new JdbcTemplate(new SingleConnectionDataSource(DriverManager.getConnection(url,"postgres",""),true));
    }
    private void close(JdbcTemplate jdbc) {((SingleConnectionDataSource)Objects.requireNonNull(jdbc.getDataSource())).destroy();}
    private void ageingRows(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TEMP TABLE tmp_contract_report(contract_id BIGINT,current_due NUMERIC,overdue_emi_count INTEGER,total_outstanding NUMERIC) ON COMMIT PRESERVE ROWS");
        jdbc.execute("INSERT INTO tmp_contract_report VALUES(41,20.25,3,1000.25),(42,0,0,0)");
    }
    @Test void appendsMetadataAndPreservesEveryOriginalValueAndDefaultArgument() throws Exception {
        var jdbc=connection();try {
            ageingRows(jdbc);
            var original=jdbc.queryForMap("SELECT * FROM public.fn_doc_reminder_letter(41,'2026-10-17')");
            assertThat(original).hasSize(11);
            migrate();
            var updated=jdbc.queryForMap("SELECT * FROM public.fn_doc_reminder_letter(41,'2026-10-17')");
            assertThat(updated).hasSize(13).containsAllEntriesOf(original)
                .containsEntry("CONTRACT_NUMBER","CN/2026-0041").containsEntry("CONTRACT_DATE","17 Oct 2026");
            assertThat(jdbc.queryForMap("SELECT * FROM public.fn_doc_reminder_letter(41)")).containsEntry("CONTRACT_DATE","17 Oct 2026");
            assertThat(jdbc.queryForMap("SELECT * FROM public.fn_doc_reminder_letter(42,'2026-10-17')"))
                .containsEntry("CONTRACT_NUMBER",null).containsEntry("CONTRACT_DATE",null);
            for(int month=1;month<=12;month++) {
                jdbc.update("UPDATE contracts SET contract_date=make_date(2026,?,7) WHERE contract_id=41",month);
                assertThat(jdbc.queryForObject("SELECT \"CONTRACT_DATE\" FROM public.fn_doc_reminder_letter(41,'2026-10-17')",String.class))
                    .isEqualTo("07 "+List.of("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec").get(month-1)+" 2026");
            }
        }finally {close(jdbc);}
    }
    @Test void retainsAgeingRequirementAndAllExistingEligibilityFilters() throws Exception {
        migrate();var jdbc=connection();try {
            assertThatThrownBy(()->jdbc.queryForList("SELECT * FROM public.fn_doc_reminder_letter(41,'2026-10-17')"))
                .hasMessageContaining("Aging Analysis has not been run");
            ageingRows(jdbc);
            for(String disqualifier:List.of("is_active=false","is_draft=true","status='N'","loan_close_date=DATE '2026-10-17'")) {
                jdbc.update("UPDATE contracts SET "+disqualifier+" WHERE contract_id=41");
                assertThat(jdbc.queryForList("SELECT * FROM public.fn_doc_reminder_letter(41,'2026-10-17')")).isEmpty();
                jdbc.update("UPDATE contracts SET is_active=true,is_draft=false,status='Y',loan_close_date=NULL WHERE contract_id=41");
            }
        }finally {close(jdbc);}
    }
    @Test void preservesOwnerRestrictedExecuteGrantsAndComment() throws Exception {
        var jdbc=connection();try {
            jdbc.execute("ALTER FUNCTION public.fn_doc_reminder_letter(bigint,date) OWNER TO pg_read_all_data; REVOKE ALL ON FUNCTION public.fn_doc_reminder_letter(bigint,date) FROM PUBLIC; GRANT EXECUTE ON FUNCTION public.fn_doc_reminder_letter(bigint,date) TO pg_monitor WITH GRANT OPTION; COMMENT ON FUNCTION public.fn_doc_reminder_letter(bigint,date) IS 'Retain reminder access policy'");
            String acl="SELECT grantee::regrole::text AS role,privilege_type,is_grantable FROM pg_proc p CROSS JOIN LATERAL aclexplode(p.proacl) WHERE p.oid='public.fn_doc_reminder_letter(bigint,date)'::regprocedure ORDER BY grantee,privilege_type";
            var before=jdbc.queryForList(acl);migrate();
            assertThat(jdbc.queryForList(acl)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT pg_get_userbyid(proowner) FROM pg_proc WHERE oid='public.fn_doc_reminder_letter(bigint,date)'::regprocedure",String.class)).isEqualTo("pg_read_all_data");
            assertThat(jdbc.queryForObject("SELECT obj_description('public.fn_doc_reminder_letter(bigint,date)'::regprocedure,'pg_proc')",String.class)).isEqualTo("Retain reminder access policy");
        }finally {close(jdbc);}
    }
    @Test void dependentViewFailsSafelyWithoutDroppingTheViewOrChangingTheFunction() throws Exception {
        var jdbc=connection();try {
            jdbc.execute("CREATE VIEW reminder_dependent AS SELECT * FROM public.fn_doc_reminder_letter(41,'2026-10-17')");
            String original=jdbc.queryForObject("SELECT pg_get_functiondef('public.fn_doc_reminder_letter(bigint,date)'::regprocedure)",String.class);
            Files.copy(migration,temp.resolve(migration.getFileName()));
            assertThatThrownBy(()->flyway.migrate()).hasMessageContaining("depend");
            assertThat(jdbc.queryForObject("SELECT to_regclass('reminder_dependent') IS NOT NULL",Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT pg_get_functiondef('public.fn_doc_reminder_letter(bigint,date)'::regprocedure)",String.class)).isEqualTo(original);
        }finally {close(jdbc);}
    }
    @Test void unexpectedFinancialCustomizationAbortsInsteadOfOverwritingIt() throws Exception {
        var jdbc=connection();try {
            String customized=Files.readString(Path.of("docs/reminder-letter-function-before-v078.sql")).replace("ROUND(COALESCE(r.current_due, 0), 2)","ROUND(COALESCE(r.current_due, 0), 3)");
            jdbc.execute(customized);
            Files.copy(migration,temp.resolve(migration.getFileName()));
            assertThatThrownBy(()->flyway.migrate()).hasMessageContaining("differs from the inspected baseline");
            assertThat(jdbc.queryForObject("SELECT prosrc FROM pg_proc WHERE oid='public.fn_doc_reminder_letter(bigint,date)'::regprocedure",String.class)).contains("ROUND(COALESCE(r.current_due, 0), 3)");
        }finally {close(jdbc);}
    }
}
