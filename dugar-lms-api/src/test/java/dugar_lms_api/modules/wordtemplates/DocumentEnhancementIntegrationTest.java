package dugar_lms_api.modules.wordtemplates;

import dugar_lms_api.modules.wordtemplates.automation.*;
import dugar_lms_api.modules.wordtemplates.dto.*;
import dugar_lms_api.modules.wordtemplates.repository.*;
import dugar_lms_api.modules.wordtemplates.service.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.*;
import java.net.http.*;
import java.net.http.HttpClient.Version;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.net.ssl.SSLSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="DOCUMENT_MENU_TEST_URL",matches=".+")
class DocumentEnhancementIntegrationTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;DocumentAutomationService automation;DocumentFieldService fields;TranslationCacheService cache;
    WordTemplateRepository templates;DocumentUsageReportService reports;FakeGateway gateway;
    UsernamePasswordAuthenticationToken auth;
    final UUID gatewayAudit=UUID.fromString("00000000-0000-0000-0000-000000000001");
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("DOCUMENT_MENU_TEST_URL");
        if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/document_menu_test"))throw new IllegalArgumentException("Disposable local DB required");
        var source=Path.of("src/main/resources/db/migration");Path migrations=Files.createDirectory(temp.resolve("migrations"));
        for(String file:List.of("V000__create_core_lms_tables.sql","V006__create_party_masters.sql","V047__create_user_roles.sql","V060__create_word_document_menus_and_tables.sql","V061__grant_word_document_permissions_to_super_admin.sql","V062__create_super_admin_role_and_word_document_permissions.sql","V063__add_word_template_group_data_source.sql","V067__rename_document_menus_and_restore_permissions.sql","V068__document_languages_and_logical_types.sql","V069__document_conversion_cache_audit_and_ai_billing.sql","V070__document_ai_administration_menus.sql","V071__add_internal_document_template_group.sql","V072__retire_document_ai_settings_menu.sql","V073__document_export_jobs.sql","V074__document_export_job_constraints.sql","V075__reminder_letter_calculation_snapshots.sql","V076__retire_document_template_groups.sql","V077__registered_template_files_and_audit.sql","V078__reminder_letter_contract_placeholders.sql"))Files.copy(source.resolve(file),migrations.resolve(file));
        Files.writeString(migrations.resolve("V059__test_roles.sql"),"INSERT INTO roles(role_name,role_code) VALUES('SUPER_ADMIN','SUPER_ADMIN'),('OPERATIONS','OPERATIONS');");
        Files.writeString(migrations.resolve("V064__test_existing_template.sql"),"INSERT INTO word_template_groups(id,group_name) VALUES(9,'Existing'); INSERT INTO word_templates(id,group_id,template_name,original_filename,storage_path,display_order) VALUES(9,9,'Existing Document','existing.docx','existing.docx',7);");
        Files.writeString(migrations.resolve("V065__test_reminder_definitions.sql"),"INSERT INTO word_templates(id,group_id,template_name,original_filename,storage_path,is_active) VALUES(6,9,'Reminder Letter','','',false),(7,9,'Reminder Letter Hindi','','',false);");
        Files.writeString(migrations.resolve("V072_1__test_existing_hindi_definition.sql"),"UPDATE word_templates SET document_key='Reminder Letter',language_code='hi' WHERE id=7;");
        Files.writeString(migrations.resolve("V079__test_records.sql"),"""
            ALTER TABLE users ADD COLUMN user_group TEXT DEFAULT 'admin',ADD COLUMN user_type TEXT DEFAULT 'USER';
            INSERT INTO users(user_id,username,password_hash,role_id) VALUES(101,'tester','not-a-password',1);
            CREATE TABLE user_areas(user_id BIGINT,area_code TEXT);
            CREATE TABLE user_contracts(user_id BIGINT,contract_number TEXT);
            CREATE TABLE contracts(contract_id BIGINT PRIMARY KEY,contract_number TEXT,loan_amount NUMERIC,contract_date DATE,borrower_code TEXT,area_code TEXT,updated_by TEXT);
            INSERT INTO contracts VALUES(41,'C-41',1234.50,'2026-10-16','B1','A1','101');
            INSERT INTO word_template_groups(id,group_name) VALUES(10,'Reminders');
            INSERT INTO word_templates(id,template_name,document_key,language_code,original_filename,storage_path)
            VALUES(11,'Reminder English','reminder','en','Reminder_EN.docx','english.docx'),
                  (12,'Reminder Hindi','reminder','hi','Reminder_HI.docx','hindi.docx');
            SELECT setval(pg_get_serial_sequence('word_templates','id'),(SELECT MAX(id) FROM word_templates),true);
            INSERT INTO ai_pricing_versions(model,input_per_million,cached_input_per_million,output_per_million,usd_inr,multiplier,effective_at,is_active)
            VALUES('gpt-5-mini',0.25,0.025,2,84,3,CURRENT_TIMESTAMP,TRUE);
            INSERT INTO document_generation_audit(id,contract_id,contract_number,username,user_id,status,language_code)
            VALUES('00000000-0000-0000-0000-000000000001',41,'C-41','tester',101,'STARTED','hi');
            """);
        var flyway=Flyway.configure().dataSource(url,"postgres","").locations("filesystem:"+migrations).cleanDisabled(false).load();flyway.clean();flyway.migrate();flyway.validate();assertThat(flyway.migrate().migrationsExecuted).isZero();
        var ds=new DriverManagerDataSource(url,"postgres","");jdbc=new JdbcTemplate(ds);var named=new NamedParameterJdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);
        var registry=new WordPlaceholderRegistry(named);fields=new DocumentFieldService(jdbc,registry);templates=new WordTemplateRepository(named);
        gateway=new FakeGateway(new AiUsageService(jdbc,manager,new BigDecimal("0.25"),new BigDecimal("0.025"),new BigDecimal("2"),new BigDecimal("84")));
        ProxyFactory proxy=new ProxyFactory(new TranslationCacheService(jdbc,gateway));proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));cache=(TranslationCacheService)proxy.getProxy();
        var repo=mock(WordDocumentGenerationRepository.class);
        Map<String,Object> contract=new HashMap<>();contract.put("contract_id",41L);contract.put("contract_number","C-41");contract.put("loan_amount",new BigDecimal("1234.50"));contract.put("contract_date",LocalDate.of(2026,10,16));contract.put("borrower_code","B1");
        when(repo.findAccessibleContract(eq(41L),any())).thenReturn(Optional.of(contract));when(repo.findAccessibleContractByNumber(eq("C-41"),any())).thenReturn(Optional.of(contract));when(repo.findParty("B1","BORROWER")).thenReturn(Optional.of(Map.of("full_name","RAMESH 42")));
        var storage=new WordTemplateStorageService(temp.toString(),10485760);var engine=new WordDocxTemplateEngine();
        var generation=new WordDocumentGenerationService(repo,templates,storage,registry,engine);
        automation=new DocumentAutomationService(jdbc,fields,cache,generation,repo,templates,storage,engine);reports=new DocumentUsageReportService(named);
        fields.saveAlias(null,"name","{{BORROWER_FULL_NAME}}","tester");fields.saveAlias(null,"Name2","{{BORROWER_FULL_NAME}}","tester");
        for(String language:List.of("english","hindi"))try(var doc=new XWPFDocument();var out=Files.newOutputStream(temp.resolve(language+".docx"))) {
            var paragraph=doc.createParagraph();paragraph.createRun().setText(language.equals("hindi")?"स्थिर अनुच्छेद {{na":"Static paragraph {{na");paragraph.createRun().setText("me}}");
            doc.createTable(1,1).getRow(0).getCell(0).setText("{{Name2}} | {{CONTRACT_CONTRACT_NUMBER}} | {{LOAN_AMOUNT_IN_WORDS}}");doc.write(out);
        }
        auth=new UsernamePasswordAuthenticationToken("tester","",List.of());auth.setDetails(Map.of("userId",101L,"userGroup","admin"));
    }
    private record ExportFixture(DocumentExportService service,List<Runnable> work,
            dugar_lms_api.modules.accessmanagement.service.MenuAccessService access,
            WordDocumentGenerationRepository contracts) implements AutoCloseable {
        public void close(){service.close();}
        UUID submit(Long template,List<Long> ids) {
            return (UUID)service.submit(new DocumentExportRequest(template,"DOCX",false,ids,null,false),exportAuth()).get("id");
        }
        private static UsernamePasswordAuthenticationToken exportAuth(){
            var auth=new UsernamePasswordAuthenticationToken("tester","",List.of());auth.setDetails(Map.of("userId",101L,"userGroup","admin"));return auth;
        }
    }
    private ExportFixture exportFixture() {
        var access=mock(dugar_lms_api.modules.accessmanagement.service.MenuAccessService.class);
        when(access.canView(anyString(),any())).thenReturn(true);
        var repo=mock(WordDocumentGenerationRepository.class);
        when(repo.isEligibleForDocument(anyLong(),any())).thenReturn(true);
        var listService=mock(dugar_lms_api.modules.contracts.service.ContractListService.class);
        when(listService.validatedCriteria(any())).thenAnswer(i -> i.getArgument(0));
        var lists=mock(dugar_lms_api.modules.contracts.repository.ContractListRepository.class);
        when(lists.snapshotDocumentSelection(any(),any(),any())).thenAnswer(i -> jdbc.update("INSERT INTO document_export_items(job_id,contract_id) VALUES(?,41)",i.getArgument(0,UUID.class)));
        List<Runnable> work=new ArrayList<>();
        var converter=mock(DocumentPdfConverter.class);
        when(converter.convert(any())).thenAnswer(i -> Files.writeString(i.getArgument(0,Path.class).resolveSibling("converted.pdf"),"%PDF-1.7\ncontrolled converter output"));
        var service=new DocumentExportService(jdbc,automation,templates,new WordTemplateStorageService(temp.toString(),10485760),repo,lists,listService,access,
            converter,new DataSourceTransactionManager(jdbc.getDataSource()),temp.resolve("exports").toString(),10485760,1000) {
            @Override protected void enqueue(Runnable task){work.add(task);}
        };
        return new ExportFixture(service,work,access,repo);
    }
    private ReminderLetterService reminderFixture() throws Exception {
        jdbc.execute("ALTER TABLE contracts ADD COLUMN is_active BOOLEAN DEFAULT TRUE,ADD COLUMN is_draft BOOLEAN DEFAULT FALSE,ADD COLUMN status TEXT DEFAULT 'Y',ADD COLUMN loan_close_date DATE");
        jdbc.execute("CREATE TABLE reminder_test_calls(pid INTEGER,as_on_date DATE)");
        jdbc.execute("""
            CREATE PROCEDURE public.sp_branch_wise_ageing_test_2(IN p_as_on_date DATE,IN p_area_code VARCHAR)
            LANGUAGE plpgsql AS $$ BEGIN
              DROP TABLE IF EXISTS pg_temp.tmp_contract_report;
              CREATE TEMP TABLE tmp_contract_report(pid INTEGER,as_on_date DATE) ON COMMIT PRESERVE ROWS;
              INSERT INTO tmp_contract_report VALUES(pg_backend_pid(),p_as_on_date);
              INSERT INTO reminder_test_calls VALUES(pg_backend_pid(),p_as_on_date);
            END $$;
            DROP FUNCTION public.fn_doc_reminder_letter(bigint,date) RESTRICT;
            CREATE FUNCTION public.fn_doc_reminder_letter(p_contract_id BIGINT,p_as_on_date DATE)
            RETURNS TABLE("BORROWER_NAME" TEXT,"BORROWER_SWD" TEXT,"VEHICLE" TEXT,"EMI" NUMERIC,"CURRENT_DUE" NUMERIC,
              "NO_OF_OVERDUES" INTEGER,"TOTAL_OS" NUMERIC,"TOTAL_OS_WORDS" TEXT,"GUARANTOR_1_NAME" TEXT,"GUARANTOR_2_NAME" TEXT,"BORROWER_ADDRESS" TEXT,"CONTRACT_NUMBER" TEXT,"CONTRACT_DATE" TEXT)
            LANGUAGE plpgsql AS $$ BEGIN
              IF NOT EXISTS(SELECT 1 FROM pg_temp.tmp_contract_report WHERE pid=pg_backend_pid() AND as_on_date=p_as_on_date) THEN
                RAISE EXCEPTION 'Wrong calculation connection/date';
              END IF;
              RETURN QUERY SELECT 'RAMESH 42'::text,'RAMESH 42'::text,'RAMESH 42'::text,1234.50::numeric,
                CASE WHEN p_contract_id=41 THEN 20.25 ELSE 0 END::numeric,CASE WHEN p_contract_id=41 THEN 0 ELSE 2 END,
                CASE WHEN p_as_on_date=DATE '2026-10-17' THEN 1000.25 ELSE 2000.75 END::numeric,'SQL amount words'::text,
                'RAMESH 42'::text,'RAMESH 42'::text,'SQL ONLY ADDRESS 42'::text,
                (SELECT contract_number::text FROM contracts WHERE contract_id=p_contract_id),
                (SELECT TO_CHAR(contract_date,'DD Mon YYYY') FROM contracts WHERE contract_id=p_contract_id);
            END $$;
            """);
        jdbc.update("INSERT INTO contracts(contract_id,contract_number,loan_amount,contract_date,borrower_code,area_code,updated_by) VALUES(42,'C-42',1,'2026-10-17','B1','A1','101')");
        var access=mock(dugar_lms_api.modules.accessmanagement.service.MenuAccessService.class);when(access.canView(anyString(),any())).thenReturn(true);
        return new ReminderLetterService(jdbc,new DataSourceTransactionManager(jdbc.getDataSource()),access,1000);
    }
    @Test void reminderGridCalculatesOnceAndPagesSearchesFiltersWithoutCallingProcedureAgain() throws Exception {
        var reminder=reminderFixture();LocalDate date=LocalDate.of(2026,10,17);
        UUID id=(UUID)reminder.snapshot(date,auth).get("id");
        assertThat(reminder.rows(id,date,false,"",0,1,auth)).containsEntry("totalElements",2L);
        assertThat(reminder.rows(id,date,false,"C-42",0,25,auth)).containsEntry("totalElements",1L);
        // Zero Current Due is excluded even with positive overdues and Total O/S.
        assertThat(reminder.rows(id,date,true,"C-42",0,25,auth)).containsEntry("totalElements",0L);
        assertThat(reminder.rows(id,date,true,"RAMESH",0,25,auth)).containsEntry("totalElements",1L);
        var row=(Map<?,?>)((List<?>)reminder.rows(id,date,true,"",0,25,auth).get("content")).getFirst();
        assertThat(row.get("emi")).isEqualTo(new BigDecimal("1234.50"));assertThat(row.get("currentDue")).isEqualTo(new BigDecimal("20.25"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reminder_test_calls",Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE users SET user_group='user',user_type='CUSTOMER' WHERE user_id=101");
        assertThat(reminder.rows(id,date,false,"",0,25,auth)).containsEntry("totalElements",0L); // do not trust stale admin group in JWT
        jdbc.update("UPDATE users SET user_group='admin',user_type='USER' WHERE user_id=101");
        var values=ReminderLetterService.values(jdbc.queryForObject("SELECT document_values FROM document_reminder_rows WHERE snapshot_id=? AND contract_id=41",String.class,id));
        assertThat(values).hasSize(13).containsEntry("{{CONTRACT_NUMBER}}","C-41").containsEntry("{{CONTRACT_DATE}}","16 Oct 2026").containsEntry("{{BORROWER_ADDRESS}}","SQL ONLY ADDRESS 42").containsEntry("{{TOTAL_OS_WORDS}}","SQL amount words");
        assertThatThrownBy(() -> reminder.rows(id,date.plusDays(1),false,"",0,25,auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        jdbc.update("UPDATE document_reminder_snapshots SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",id);
        assertThatThrownBy(() -> reminder.rows(id,date,false,"",0,25,auth)).hasMessageContaining("expired");reminder.cleanup();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_reminder_rows",Integer.class)).isZero();
    }
    @Test void reminderReusedConnectionDropsTemporaryTablesAndRecalculatesForTheNewDate() throws Exception {
        reminderFixture();
        try(var source=new SingleConnectionDataSource(jdbc.getDataSource().getConnection(),true)) {
            var local=new JdbcTemplate(source);var access=mock(dugar_lms_api.modules.accessmanagement.service.MenuAccessService.class);
            when(access.canView(anyString(),any())).thenReturn(true);
            var reminder=new ReminderLetterService(local,new DataSourceTransactionManager(source),access,1000);
            reminder.snapshot(LocalDate.of(2026,10,17),auth);
            assertThat(local.queryForObject("SELECT to_regclass('pg_temp.tmp_contract_report')::text",String.class)).isNull();
            UUID second=(UUID)reminder.snapshot(LocalDate.of(2026,10,18),auth).get("id");
            assertThat(local.queryForObject("SELECT document_values->>'TOTAL_OS' FROM document_reminder_rows WHERE snapshot_id=? AND contract_id=41",String.class,second)).isEqualTo("2000.75");
            assertThat(local.queryForObject("SELECT COUNT(DISTINCT pid) FROM reminder_test_calls",Integer.class)).isEqualTo(1);
            assertThat(local.queryForObject("SELECT COUNT(*) FROM reminder_test_calls",Integer.class)).isEqualTo(2);
        }
    }
    @Test void concurrentReminderCalculationsUseIndependentConnectionsAndDates() throws Exception {
        var reminder=reminderFixture();var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> reminder.snapshot(LocalDate.of(2026,10,17),auth));
            var second=pool.submit(() -> reminder.snapshot(LocalDate.of(2026,10,18),auth));
            UUID a=(UUID)first.get(20,TimeUnit.SECONDS).get("id"),b=(UUID)second.get(20,TimeUnit.SECONDS).get("id");
            assertThat(jdbc.queryForObject("SELECT document_values->>'TOTAL_OS' FROM document_reminder_rows WHERE snapshot_id=? AND contract_id=41",String.class,a)).isEqualTo("1000.25");
            assertThat(jdbc.queryForObject("SELECT document_values->>'TOTAL_OS' FROM document_reminder_rows WHERE snapshot_id=? AND contract_id=41",String.class,b)).isEqualTo("2000.75");
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT pid) FROM reminder_test_calls",Integer.class)).isEqualTo(2);
            try(var fixture=exportFixture()) {
                UUID jobA=fixture.submit(11L,List.of(41L)),jobB=fixture.submit(11L,List.of(41L));
                var exportA=pool.submit(() -> reminder.prepareExport(jobA,LocalDate.of(2026,10,17),auth));
                var exportB=pool.submit(() -> reminder.prepareExport(jobB,LocalDate.of(2026,10,18),auth));
                exportA.get(20,TimeUnit.SECONDS);exportB.get(20,TimeUnit.SECONDS);
                assertThat(jdbc.queryForObject("SELECT document_values->>'TOTAL_OS' FROM document_export_items WHERE job_id=?",String.class,jobA)).isEqualTo("1000.25");
                assertThat(jdbc.queryForObject("SELECT document_values->>'TOTAL_OS' FROM document_export_items WHERE job_id=?",String.class,jobB)).isEqualTo("2000.75");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reminder_test_calls",Integer.class)).isEqualTo(4);
            }
        } finally {pool.shutdownNow();}
    }
    @Test void reminderHindiUsesTranslationCacheWithoutRecalculatingOnFinalize() throws Exception {
        jdbc.update("INSERT INTO document_field_rules(canonical_code,conversion_type,context,format_kind) VALUES('{{CONTRACT_DATE}}','FORMAT','date','DATE'),('{{CONTRACT_NUMBER}}','TRANSLATE','identifier',NULL)");
        var reminder=reminderFixture();LocalDate date=LocalDate.of(2026,10,17);
        jdbc.update("UPDATE word_templates SET template_name='Reminder Letter',document_key='Reminder Letter' WHERE id=12");
        try(var doc=new XWPFDocument();var out=Files.newOutputStream(temp.resolve("hindi.docx"))) {
            for(String code:ReminderLetterService.CODES)doc.createParagraph().createRun().setText("{{"+code+"}}");doc.write(out);
        }
        UUID snapshot=(UUID)reminder.snapshot(date,auth).get("id");
        try(var fixture=exportFixture()) {
            org.springframework.test.util.ReflectionTestUtils.setField(fixture.service(),"reminders",reminder);
            for(int i=0;i<2;i++) {
                UUID id=(UUID)fixture.service().submit(new DocumentExportRequest(12L,"DOCX",false,List.of(41L),null,false,date,snapshot,true,""),auth).get("id");
                fixture.work().removeFirst().run();assertThat(fixture.service().status(id,auth,0)).containsEntry("status","COMPLETED");
                try(var input=fixture.service().download(id,auth).resource().getInputStream();var doc=new XWPFDocument(input)) {
                    assertThat(doc.getParagraphs()).hasSize(13);assertThat(doc.getParagraphs().stream().map(p -> p.getText()).toList()).contains("C-41","16 Oct 2026","1000.25",HindiFormats.format("AMOUNT_WORDS","1000.25")).noneMatch(v -> v.contains("{{"));
                }
            }
            assertThat(gateway.calls.get()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reminder_test_calls",Integer.class)).isEqualTo(3);
        }
    }
    @Test void reminderHindi401NeverFinalizesAndEnglishStillGeneratesWithoutCredentials() throws Exception {
        var reminder=reminderFixture();LocalDate date=LocalDate.of(2026,10,17);
        jdbc.update("UPDATE word_templates SET template_name='Reminder Letter',document_key='Reminder Letter' WHERE id IN (11,12)");
        for(String language:List.of("english","hindi"))try(var doc=new XWPFDocument();var out=Files.newOutputStream(temp.resolve(language+".docx"))) {
            for(String code:ReminderLetterService.CODES)doc.createParagraph().createRun().setText("{{"+code+"}}");doc.write(out);
        }
        UUID snapshot=(UUID)reminder.snapshot(date,auth).get("id");gateway.status=401;
        try(var fixture=exportFixture()) {
            org.springframework.test.util.ReflectionTestUtils.setField(fixture.service(),"reminders",reminder);
            UUID failed=(UUID)fixture.service().submit(new DocumentExportRequest(12L,"DOCX",false,List.of(41L),null,false,date,snapshot,true,""),auth).get("id");
            fixture.work().removeFirst().run();
            assertThat(fixture.service().status(failed,auth,0)).containsEntry("status","FAILED").containsEntry("success_count",0L);
            assertThatThrownBy(()->fixture.service().download(failed,auth)).isInstanceOf(IllegalArgumentException.class);
            assertThat(gateway.calls.get()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_translation_cache",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT failure_reason FROM document_generation_audit WHERE batch_id=?",String.class,failed))
                .contains("HTTP_401","OPENAI_API_KEY").doesNotContain("test-key-never-sent","Rejected credential");
            org.springframework.test.util.ReflectionTestUtils.setField(gateway,"key","");
            org.springframework.test.util.ReflectionTestUtils.setField(gateway,"enabled",false);
            UUID english=(UUID)fixture.service().submit(new DocumentExportRequest(11L,"DOCX",false,List.of(41L),null,false,date,snapshot,true,""),auth).get("id");
            fixture.work().removeFirst().run();
            assertThat(fixture.service().status(english,auth,0)).containsEntry("status","COMPLETED");
            try(var input=fixture.service().download(english,auth).resource().getInputStream();var doc=new XWPFDocument(input)) {
                assertThat(doc.getParagraphs().stream().map(p->p.getText()).toList()).contains("SQL amount words","SQL ONLY ADDRESS 42");
            }
            assertThat(gateway.calls.get()).isEqualTo(1);
        }
    }
    @Test void reminderExportCalculatesOnceAndMergesAllElevenSqlValuesIncludingAddress() throws Exception {
        var reminder=reminderFixture();LocalDate date=LocalDate.of(2026,10,17);
        jdbc.update("UPDATE word_templates SET template_name='Reminder Letter',document_key='Reminder Letter' WHERE id=11");
        try(var doc=new XWPFDocument();var out=Files.newOutputStream(temp.resolve("english.docx"))) {
            for(String code:ReminderLetterService.CODES)doc.createParagraph().createRun().setText("{{"+code+"}}");doc.write(out);
        }
        UUID snapshot=(UUID)reminder.snapshot(date,auth).get("id");
        try(var fixture=exportFixture()) {
            org.springframework.test.util.ReflectionTestUtils.setField(fixture.service(),"reminders",reminder);
            UUID id=(UUID)fixture.service().submit(new DocumentExportRequest(11L,"DOCX",true,List.of(),null,false,date,snapshot,true,"RAMESH"),auth).get("id");
            fixture.work().removeFirst().run();assertThat(fixture.service().status(id,auth,0)).containsEntry("status","COMPLETED").containsEntry("total_count",1L);
            try(var zip=new java.util.zip.ZipFile(fixture.service().download(id,auth).resource().getFile());var input=zip.getInputStream(zip.entries().nextElement());var doc=new XWPFDocument(input)) {
                assertThat(doc.getParagraphs()).hasSize(13);assertThat(doc.getParagraphs().stream().map(p -> p.getText()).toList()).contains("C-41","16 Oct 2026","SQL ONLY ADDRESS 42","SQL amount words","1234.50","1000.25").noneMatch(v -> v.contains("{{"));
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reminder_test_calls",Integer.class)).isEqualTo(2); // one grid + one batch, not one per document/finalization
            assertThat(gateway.calls.get()).isZero();
            assertThatThrownBy(() -> fixture.service().submit(new DocumentExportRequest(11L,"DOCX",false,List.of(42L),null,false,date,snapshot,true,""),auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
    }
    @Test void pdfExportDownloadsPdfAndRemovesIntermediateFiles() throws Exception {
        try(var fixture=exportFixture()) {
            UUID id=(UUID)fixture.service().submit(new DocumentExportRequest(11L,"PDF",false,List.of(41L),null,false),auth).get("id");fixture.work().removeFirst().run();
            var download=fixture.service().download(id,auth);
            assertThat(download.contentType()).isEqualTo("application/pdf");assertThat(download.name()).endsWith(".pdf");
            assertThat(Files.readString(download.resource().getFile().toPath())).startsWith("%PDF-");
            try(var paths=Files.list(temp.resolve("exports").resolve(id.toString()))) {assertThat(paths.map(p -> p.getFileName().toString()).toList()).containsExactly("download.pdf");}
            assertThat(jdbc.queryForObject("SELECT output_format FROM document_generation_audit WHERE batch_id=?",String.class,id)).isEqualTo("PDF");
        }
    }
    @Test void exportEligibilityAndDownloadScopeQueriesRespectActiveContracts() {
        jdbc.execute("ALTER TABLE contracts ADD COLUMN is_active BOOLEAN DEFAULT TRUE,ADD COLUMN status TEXT DEFAULT 'Y'");
        jdbc.execute("CREATE TABLE contract_repayment_structures(contract_id BIGINT)");jdbc.update("INSERT INTO contract_repayment_structures VALUES(41)");
        var repo=new WordDocumentGenerationRepository(new NamedParameterJdbcTemplate(jdbc));
        var admin=dugar_lms_api.modules.reports.ReportAccessScope.from(auth);
        assertThat(repo.isEligibleForDocument(41L,admin)).isTrue();
        assertThat(repo.isEligibleForDocument(42L,admin)).isFalse();
        assertThat(repo.isEligibleForDocument(41L,new dugar_lms_api.modules.reports.ReportAccessScope(false,202L,"user"))).isFalse();
        try(var fixture=exportFixture()) {
            UUID id=fixture.submit(11L,List.of(41L));fixture.work().removeFirst().run();
            assertThat(repo.exportHasInaccessibleContracts(id,admin)).isFalse();
            jdbc.update("UPDATE contracts SET is_active=FALSE WHERE contract_id=41");
            assertThat(repo.isEligibleForDocument(41L,admin)).isFalse();assertThat(repo.exportHasInaccessibleContracts(id,admin)).isTrue();
        }
    }
    @Test void singleExportSurvivesServiceRestartAndEnforcesOwnershipAndFreshAccess() throws Exception {
        UUID id;
        try(var fixture=exportFixture()) {
            id=fixture.submit(11L,List.of(41L));fixture.work().removeFirst().run();
            assertThat(fixture.service().status(id,auth,0)).containsEntry("status","COMPLETED").containsEntry("success_count",1L);
            var download=fixture.service().download(id,auth);
            assertThat(download.name()).endsWith("_C-41.docx");
            try(var input=download.resource().getInputStream();var doc=new XWPFDocument(input)){assertThat(doc.getParagraphs().getFirst().getText()).contains("RAMESH 42");}
            assertThat(Files.exists(download.resource().getFile().toPath())).isTrue();
            var other=new UsernamePasswordAuthenticationToken("other","",List.of());other.setDetails(Map.of("userId",202L));
            jdbc.update("INSERT INTO users(user_id,username,password_hash,role_id) VALUES(202,'other','x',1)");
            assertThatThrownBy(() -> fixture.service().status(id,other,0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            when(fixture.contracts().exportHasInaccessibleContracts(eq(id),any())).thenReturn(true);
            assertThatThrownBy(() -> fixture.service().download(id,auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
        try(var restarted=exportFixture()) {assertThat(restarted.service().download(id,auth).resource().exists()).isTrue();}
        assertThat(gateway.calls.get()).isZero();
    }
    @Test void bulkExportContainsSuccessfulDocumentsAndExplicitErrorsOnly() throws Exception {
        try(var fixture=exportFixture()) {
            UUID id=fixture.submit(11L,List.of(41L,42L,41L));fixture.work().removeFirst().run();
            var status=fixture.service().status(id,auth,0);
            assertThat(status).containsEntry("status","COMPLETED_WITH_ERRORS").containsEntry("total_count",2L).containsEntry("success_count",1L).containsEntry("failure_count",1L);
            try(var zip=new java.util.zip.ZipFile(fixture.service().download(id,auth).resource().getFile())) {
                assertThat(zip.size()).isEqualTo(2);assertThat(zip.getEntry("Reminder English_C-41.docx")).isNotNull();
                assertThat(new String(zip.getInputStream(zip.getEntry("errors.csv")).readAllBytes(),StandardCharsets.UTF_8)).contains("42", "Contract is not eligible or accessible");
            }
            assertThat(Files.exists(temp.resolve("exports").resolve(id.toString()).resolve("current.docx"))).isFalse();
        }
    }
    @Test void hindiBulkUsesCachedTranslationAndAllMatchingProducesZip() throws Exception {
        try(var fixture=exportFixture()) {
            UUID id=(UUID)fixture.service().submit(new DocumentExportRequest(12L,"DOCX",true,List.of(),null,false),auth).get("id");
            fixture.work().removeFirst().run();
            assertThat(fixture.service().status(id,auth,0)).containsEntry("status","COMPLETED");
            assertThat(fixture.service().download(id,auth).name()).endsWith(".zip");
            UUID second=fixture.submit(12L,List.of(41L));fixture.work().removeFirst().run();
            assertThat(fixture.service().status(second,auth,0)).containsEntry("status","COMPLETED");
            assertThat(gateway.calls.get()).isEqualTo(1);
        }
    }
    @Test void expiredWorkerIsFencedCleanedAndNeverReplayed() throws Exception {
        try(var fixture=exportFixture()) {
            UUID id=fixture.submit(11L,List.of(41L,42L));
            Path directory=Files.createDirectories(temp.resolve("exports").resolve(id.toString()));Files.writeString(directory.resolve("output.part"),"partial");
            jdbc.update("UPDATE document_export_jobs SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE id=?",id);
            fixture.service().cleanup();
            assertThat(fixture.service().status(id,auth,0)).containsEntry("status","FAILED").containsEntry("failure_count",2L);
            assertThat(Files.exists(directory)).isFalse();fixture.work().removeFirst().run();
            assertThat(Files.exists(directory)).isFalse();assertThat(gateway.calls.get()).isZero();
            assertThatThrownBy(() -> fixture.service().download(id,auth)).hasMessageContaining("not ready");
        }
    }
    @Test void exportRechecksPermissionTemplateHashAndExpiry() throws Exception {
        try(var fixture=exportFixture()) {
            UUID id=fixture.submit(11L,List.of(41L));Files.writeString(temp.resolve("english.docx"),"changed");fixture.work().removeFirst().run();
            assertThat(fixture.service().status(id,auth,0)).containsEntry("status","FAILED");
            assertThat(fixture.service().status(id,auth,0).get("errors").toString()).contains("Master document changed");
            jdbc.update("UPDATE document_export_jobs SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE id=?",id);fixture.service().cleanup();
            assertThat(fixture.service().status(id,auth,0)).containsEntry("status","EXPIRED");
            when(fixture.access().canView(anyString(),any())).thenReturn(false);
            assertThatThrownBy(() -> fixture.service().status(id,auth,0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
    }
    @Test void inlineAliasesCanBeEditedClearedAndResolveCanonicalValues() {
        fields.saveAlias(null," {{add1}} ","{{BORROWER_ADDRESS_LINE_1}}","tester");
        Long id=jdbc.queryForObject("SELECT id FROM document_placeholder_aliases WHERE alias='add1'",Long.class);
        assertThat(fields.canonical("{{ADD1}}",fields.aliases())).isEqualTo("{{BORROWER_ADDRESS_LINE_1}}");
        fields.saveAlias(id,"addr","{{BORROWER_ADDRESS_LINE_1}}","tester");
        assertThat(fields.aliases()).doesNotContainKey("add1").containsEntry("addr","{{BORROWER_ADDRESS_LINE_1}}");
        assertThatThrownBy(() -> fields.saveAlias(null,"ADDR","{{BORROWER_FULL_NAME}}","tester")).hasMessageContaining("already in use");
        assertThatThrownBy(() -> fields.saveAlias(id,"borrower_full_name","{{BORROWER_ADDRESS_LINE_1}}","tester")).hasMessageContaining("collides");
        fields.deleteAlias(id);assertThat(fields.aliases()).doesNotContainKey("addr").containsKeys("name","name2");
    }
    @Test void settingsMigrationRetiresOnlySettingsAndPreservesHistoricalPricingAndReport() {
        assertThat(jdbc.queryForObject("SELECT is_active OR is_visible FROM menus WHERE menu_code='ADMINISTRATION_MASTERS_DOCUMENT_AI_SETTINGS'",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT is_active AND is_visible FROM menus WHERE menu_code='ADMINISTRATION_REPORTS_AI_USAGE_BILLING'",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_pricing_versions",Integer.class)).isEqualTo(2);
        var rates=new AiUsageService(jdbc,new DataSourceTransactionManager(jdbc.getDataSource()),new BigDecimal("0.25"),new BigDecimal("0.025"),new BigDecimal("2"),new BigDecimal("84")).pricing("gpt-5-mini");
        assertThat(rates).containsEntry("multiplier",new BigDecimal("3")).containsEntry("source","BACKEND_CONFIGURATION").doesNotContainKey("id");
    }
    @Test void contractNumberLookupRetainsAccessScopeAndRejectsPartialMatches() {
        var repo=new WordDocumentGenerationRepository(new NamedParameterJdbcTemplate(jdbc));
        var admin=new dugar_lms_api.modules.reports.ReportAccessScope(true,101L,"admin");
        var restricted=new dugar_lms_api.modules.reports.ReportAccessScope(false,999L,"user");
        assertThat(repo.findAccessibleContractByNumber(" c-41 ",admin)).isPresent();
        assertThat(repo.findAccessibleContractByNumber("C-4",admin)).isEmpty();
        assertThat(repo.findAccessibleContractByNumber("C-41",restricted)).isEmpty();
    }
    @Test void contractFieldPreviewUsesEnglishDatabaseValuesWithoutAiOrAudit() {
        var preview=automation.contractFieldValues("  C-41  ",auth);
        assertThat(preview.get("contractNumber")).isEqualTo("C-41");
        assertThat(preview.get("values").toString()).contains("RAMESH 42","C-41","1,234.50");
        assertThat(gateway.calls.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_api_usage",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_generation_audit",Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> automation.contractFieldValues("UNKNOWN",auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void forwardMigrationsPreserveExistingIdsAndTolerateAnAlreadyRemovedGroupColumn() throws Exception {
        var before=jdbc.queryForList("SELECT id,template_name,language_code,original_filename,storage_path,is_active FROM word_templates ORDER BY id");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V076__retire_document_template_groups.sql")));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V077__registered_template_files_and_audit.sql")));
        assertThat(jdbc.queryForList("SELECT id,template_name,language_code,original_filename,storage_path,is_active FROM word_templates ORDER BY id")).isEqualTo(before);
        assertThat(templates.findTemplate(6L).orElseThrow().languageCode()).isEqualTo("en");
        assertThat(templates.findTemplate(7L).orElseThrow().languageCode()).isEqualTo("hi");
    }
    @Test void missingReminderDefinitionsAreRegisteredInactiveWithoutInventingFiles() throws Exception {
        jdbc.update("DELETE FROM word_templates WHERE id IN (6,7)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V077__registered_template_files_and_audit.sql")));
        var variants=jdbc.queryForList("SELECT id,language_code,is_active,storage_path,original_filename FROM word_templates WHERE document_key='Reminder Letter' ORDER BY language_code");
        assertThat(variants).hasSize(2);
        assertThat(variants.stream().map(row -> row.get("language_code"))).containsExactly("en","hi");
        for(var row:variants) {assertThat(((Number)row.get("id")).longValue()).isGreaterThan(12);assertThat(row.get("is_active")).isEqualTo(false);assertThat(row.get("storage_path")).isNull();assertThat(row.get("original_filename")).isNull();}
    }
    @Test void conflictingLegacyVariantsFailMigrationWithoutRemovingTheGroupColumn() throws Exception {
        jdbc.execute("ALTER TABLE word_templates ADD COLUMN group_id BIGINT; DROP INDEX uq_document_active_language");
        jdbc.update("UPDATE word_templates SET document_key='collision',language_code='en' WHERE id IN (11,12)");
        var tx=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        String migration=Files.readString(Path.of("src/main/resources/db/migration/V076__retire_document_template_groups.sql"));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> jdbc.execute(migration))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='word_templates' AND column_name='group_id'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM word_templates WHERE document_key='collision'",Integer.class)).isEqualTo(2);
    }
    @Test void registeredDefinitionsOnlyAllowFileReplacementAndActivation() throws Exception {
        var access=mock(dugar_lms_api.modules.accessmanagement.service.MenuAccessService.class);
        when(access.canView(anyString(),any())).thenReturn(true);
        var service=new WordTemplateService(templates,new WordTemplateStorageService(temp.toString(),10485760),access,new WordPlaceholderRegistry(new NamedParameterJdbcTemplate(jdbc)),new WordDocxTemplateEngine());
        org.springframework.test.util.ReflectionTestUtils.setField(service,"documentFields",fields);
        var file=new org.springframework.mock.web.MockMultipartFile("file","new.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",Files.readAllBytes(temp.resolve("english.docx")));
        assertThatThrownBy(() -> service.createTemplate(null,file,auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.updateTemplate(11L,null,auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteTemplate(11L,auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        var replaced=service.replaceTemplateFile(11L,file,auth);
        assertThat(replaced.originalFilename()).isEqualTo("new.docx");
        assertThat(replaced.templateName()).isEqualTo("Reminder English");
        assertThat(replaced.hasFile()).isTrue();assertThat(replaced.documentKey()).isEqualTo("reminder");
        assertThat(service.setActive(11L,false,auth).isActive()).isFalse();
        assertThat(service.setActive(11L,true,auth).isActive()).isTrue();
        assertThat(jdbc.queryForList("SELECT action FROM word_template_file_audit WHERE template_id=11 ORDER BY id",String.class)).containsExactly("REPLACE","DEACTIVATE","ACTIVATE");
        var bad=new org.springframework.mock.web.MockMultipartFile("file","bad.docx","application/octet-stream",new byte[]{1,2,3});
        assertThatThrownBy(() -> service.replaceTemplateFile(11L,bad,auth)).isInstanceOf(IllegalArgumentException.class);
        assertThat(templates.findTemplate(11L).orElseThrow().originalFilename()).isEqualTo("new.docx");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM word_templates WHERE document_key='Reminder Letter'",Integer.class)).isEqualTo(2);
        Long pending=jdbc.queryForObject("SELECT id FROM word_templates WHERE document_key='Reminder Letter' AND language_code='hi'",Long.class);
        assertThatThrownBy(() -> service.setActive(pending,true,auth)).hasMessageContaining("Upload a valid DOCX");
        assertThatThrownBy(() -> service.replaceTemplateFile(pending,file,auth)).hasMessageContaining("Unknown placeholders");
        var bytes=new java.io.ByteArrayOutputStream();
        try(var doc=new XWPFDocument()) {doc.createParagraph().createRun().setText("{{BORROWER_NAME}}");doc.write(bytes);}
        var partial=new org.springframework.mock.web.MockMultipartFile("file","partial.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",bytes.toByteArray());
        assertThatThrownBy(() -> service.replaceTemplateFile(pending,partial,auth)).hasMessageContaining("Missing required placeholders");
        bytes.reset();
        try(var doc=new XWPFDocument()) {for(String code:ReminderLetterService.CODES)doc.createParagraph().createRun().setText("{{"+code+"}}");doc.write(bytes);}
        var full=new org.springframework.mock.web.MockMultipartFile("file","Reminder_HI.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",bytes.toByteArray());
        assertThat(service.replaceTemplateFile(pending,full,auth).hasFile()).isTrue();
        var generation=new WordDocumentGenerationService(mock(WordDocumentGenerationRepository.class),templates,
            new WordTemplateStorageService(temp.toString(),10485760),new WordPlaceholderRegistry(new NamedParameterJdbcTemplate(jdbc)),new WordDocxTemplateEngine());
        assertThat(generation.validate(pending,"CONTRACT").recognized()).contains("{{CONTRACT_NUMBER}}","{{CONTRACT_DATE}}");
        assertThat(generation.validate(pending,"CONTRACT").unknown()).isEmpty();
        bytes.reset();
        try(var doc=new XWPFDocument()) {for(String code:ReminderLetterService.CODES)if(!Set.of("CONTRACT_NUMBER","CONTRACT_DATE").contains(code))doc.createParagraph().createRun().setText("{{"+code+"}}");doc.write(bytes);}
        var legacy=new org.springframework.mock.web.MockMultipartFile("file","Legacy_HI.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",bytes.toByteArray());
        assertThat(service.replaceTemplateFile(pending,legacy,auth).hasFile()).isTrue();

        assertThat(service.setActive(pending,true,auth).isActive()).isTrue();
        assertThat(templates.findTemplate(pending).orElseThrow().languageCode()).isEqualTo("hi");
        assertThat(templates.findTemplate(pending).orElseThrow().id()).isEqualTo(7L);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='word_templates' AND column_name='group_id'",Integer.class)).isZero();
        assertThat(gateway.calls.get()).isZero();
    }
    @Test void englishAliasesAndUploadVariantsPreserveExistingGenerationWithoutApi() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(gateway,"key","");
        org.springframework.test.util.ReflectionTestUtils.setField(gateway,"enabled",false);
        var preview=automation.preview(new GenerateWordDocumentRequest("CONTRACT",41L,11L),"en",auth);
        var result=automation.finalizeDocument(UUID.fromString(preview.get("generationId").toString()),auth);
        try(var doc=new XWPFDocument(result.resource().getInputStream())){assertThat(doc.getParagraphs().getFirst().getText()).isEqualTo("Static paragraph RAMESH 42");assertThat(doc.getTables().getFirst().getText()).contains("C-41");}
        assertThat(gateway.calls.get()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_api_usage",Integer.class)).isZero();
        var noApiRows=(List<?>)reports.report(Map.of("contractNumber","C-41"),auth,0,50).get("rows");
        assertThat(noApiRows.toString()).contains("api_status=NO_API","billable_inr=0");
        assertThat(templates.findTemplate(11L).orElseThrow().languageCode()).isEqualTo("en");assertThat(templates.findTemplate(12L).orElseThrow().documentKey()).isEqualTo("reminder");
        var existing=templates.findTemplate(9L).orElseThrow();assertThat(existing.languageCode()).isEqualTo("en");assertThat(existing.documentKey()).isEqualTo("Existing Document");assertThat(existing.displayOrder()).isEqualTo(7);
        assertThatThrownBy(() -> templates.createTemplate(new WordTemplateMetadataRequest("Duplicate",0,true,"hi","reminder"),"duplicate.docx","another.docx","tester"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void hindiCachesApprovalsVersionsBillingAndFilteredExports() throws Exception {
        var p=automation.preview(new GenerateWordDocumentRequest("CONTRACT",41L,12L),"hi",auth);UUID id=UUID.fromString(p.get("generationId").toString());
        var draft=automation.previewDocument(id,auth);assertThat(draft.filename()).startsWith("PREVIEW-");
        try(var stream=draft.resource().getInputStream();var doc=new XWPFDocument(stream)){assertThat(doc.getParagraphs().getFirst().getText()).contains("स्थिर अनुच्छेद");}
        assertThatThrownBy(() -> automation.finalizeDocument(id,auth)).hasMessageContaining("approve");
        automation.approve(id,Map.of("{{BORROWER_FULL_NAME}}","रामेश 42"),true,auth);
        var result=automation.finalizeDocument(id,auth);
        try(var doc=new XWPFDocument(result.resource().getInputStream())){assertThat(doc.getParagraphs().getFirst().getText()).isEqualTo("स्थिर अनुच्छेद रामेश 42");assertThat(doc.getTables().getFirst().getText()).contains("C-41","रुपये");}
        var p2=automation.preview(new GenerateWordDocumentRequest("CONTRACT",41L,12L),"hi",auth);
        assertThat(gateway.calls.get()).isEqualTo(1);assertThat(((Number)p2.get("cacheHits")).longValue()).isEqualTo(1);
        assertThat(p2.toString()).contains("रामेश 42");
        var usage=jdbc.queryForMap("SELECT * FROM ai_api_usage");assertThat(usage.get("input_tokens")).isEqualTo(1000L);assertThat(usage.get("cached_input_tokens")).isEqualTo(200L);
        assertThat((BigDecimal)usage.get("estimated_usd")).isEqualByComparingTo("0.000405");assertThat((BigDecimal)usage.get("billable_inr")).isEqualByComparingTo("0.10206");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_generation_translation_versions WHERE generation_id=?",Integer.class,id)).isEqualTo(1);
        var report=reports.report(Map.of("contractNumber","C-41","language","hi"),auth,0,50);assertThat(((Map<?,?>)report.get("summary")).get("api_requests")).isEqualTo(1L);
        assertThat(reports.detail(id,auth).toString()).contains("translationVersions");
        assertThat(reports.printHtml(Map.of("contractNumber","C-41"),auth)).contains("AI API Usage &amp; Billing","C-41");
        try(var book=new XWPFDocument()) {assertThat(reports.excel(Map.of("contractNumber","C-41"),auth)).isNotEmpty();}
    }
    @Test void aliasesAreCaseInsensitiveUniqueAndIdentifiersCannotBeTranslated() {
        assertThat(fields.canonical("{{NAME}}",fields.aliases())).isEqualTo("{{BORROWER_FULL_NAME}}");
        assertThatThrownBy(() -> fields.saveAlias(null,"NaMe","{{BORROWER_FULL_NAME}}","tester")).hasMessageContaining("already in use");
        assertThatThrownBy(() -> fields.saveAlias(null,"BORROWER_FULL_NAME","{{BORROWER_FULL_NAME}}","tester")).hasMessageContaining("collides");
        assertThatThrownBy(() -> fields.saveAlias(null,"bad-alias","{{BORROWER_FULL_NAME}}","tester")).hasMessageContaining("letters");
        assertThatThrownBy(() -> fields.saveRule("{{CONTRACT_CONTRACT_NUMBER}}","TRANSLATE","identity",null,"tester")).hasMessageContaining("PRESERVE");
    }
    @Test void concurrentRequestsShareOneProviderCall() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Object> task=() -> automation.preview(new GenerateWordDocumentRequest("CONTRACT",41L,12L),"hi",auth);
            var first=pool.submit(task);var second=pool.submit(task);first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);
        }
        assertThat(gateway.calls.get()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_api_usage",Integer.class)).isEqualTo(1);
    }
    @Test void failuresRemainAuditableAndNeverFinalizeHindi() {
        gateway.fail=true;
        assertThatThrownBy(() -> automation.preview(new GenerateWordDocumentRequest("CONTRACT",41L,12L),"hi",auth)).hasMessageContaining("failed");
        assertThat(gateway.calls.get()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_api_usage WHERE status='FAILED'",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_generation_audit WHERE status='FAILED'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cache_misses FROM document_generation_audit WHERE status='FAILED'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_translation_cache",Integer.class)).isZero();
    }
    @Test void deterministicHindiFormatsAndMonetaryMultiplier() {
        assertThat(HindiFormats.format("AMOUNT_WORDS","99.995")).isEqualTo("एक सौ रुपये मात्र");
        assertThat(HindiFormats.format("DATE","2026-10-16")).isEqualTo("16 अक्टूबर 2026");
        assertThat(AiUsageService.cost(1000,200,100,new BigDecimal("0.25"),new BigDecimal("0.025"),new BigDecimal("2"))).isEqualByComparingTo("0.000405");
        assertThatThrownBy(() -> OpenAiTranslationGateway.validateHindi("NAME 42","नाम 43")).hasMessageContaining("numeric");
    }
    @Test void uploadsBothLanguagesAndReplacesIndependently() throws Exception {
        var access=mock(dugar_lms_api.modules.accessmanagement.service.MenuAccessService.class);when(access.canView(anyString(),eq(auth))).thenReturn(true);
        var named=new NamedParameterJdbcTemplate(jdbc);var registry=new WordPlaceholderRegistry(named);
        var service=new WordTemplateService(templates,new WordTemplateStorageService(temp.toString(),10485760),access,registry,new WordDocxTemplateEngine());
        org.springframework.test.util.ReflectionTestUtils.setField(service,"documentFields",fields);
        byte[] bytes=Files.readAllBytes(temp.resolve("english.docx"));
        var en=templates.createTemplate(new WordTemplateMetadataRequest("Agreement EN",0,true,"en","agreement"),"Agreement_EN.docx","english.docx","tester");
        var hi=templates.createTemplate(new WordTemplateMetadataRequest("Agreement HI",1,true,"hi","agreement"),"Agreement_HI.docx","hindi.docx","tester");
        assertThat(hi.documentKey()).isEqualTo(en.documentKey());assertThat(hi.languageCode()).isEqualTo("hi");
        service.replaceTemplateFile(hi.id(),new org.springframework.mock.web.MockMultipartFile("file","Replacement_HI.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",bytes),auth);
        assertThat(templates.findTemplate(en.id()).orElseThrow().originalFilename()).isEqualTo("Agreement_EN.docx");
        assertThat(templates.findTemplate(hi.id()).orElseThrow().originalFilename()).isEqualTo("Replacement_HI.docx");
    }
    @Test void bulkAllocationsSumToOneAndDoNotDuplicateProviderCost() {
        var manager=new DataSourceTransactionManager(jdbc.getDataSource());
        var batches=new DocumentBatchService(automation,jdbc,manager);
        var result=batches.preview(List.of(new GenerateWordDocumentRequest("CONTRACT",41L,12L,"hi",null),new GenerateWordDocumentRequest("CONTRACT",41L,12L,"hi",null)),auth);
        assertThat(result.get("batchId")).isNotNull();assertThat(gateway.calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_generation_api_links",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(allocation) FROM document_generation_api_links",BigDecimal.class)).isEqualByComparingTo("1");
        assertThat(jdbc.queryForObject("SELECT SUM(u.billable_inr*l.allocation) FROM document_generation_api_links l JOIN ai_api_usage u ON u.id=l.usage_id",BigDecimal.class)).isEqualByComparingTo("0.10206");
    }
    @Test void auditOwnershipAndSelectionPreventCrossDocumentFinalization() {
        var p=automation.preview(new GenerateWordDocumentRequest("CONTRACT",41L,11L),"en",auth);var id=UUID.fromString(p.get("generationId").toString());
        assertThatThrownBy(() -> automation.validateSelection(id,new GenerateWordDocumentRequest("CONTRACT",41L,12L),auth)).hasMessageContaining("different");
        var other=new UsernamePasswordAuthenticationToken("another","",List.of());other.setDetails(Map.of("userId",999L,"userGroup","user"));
        assertThatThrownBy(() -> automation.owned(id,other)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        var restricted=new UsernamePasswordAuthenticationToken("tester","",List.of());restricted.setDetails(Map.of("userId",101L,"userGroup","user"));
        assertThat(((Number)reports.report(Map.of(),restricted,0,50).get("total")).longValue()).isZero();
    }
    @Test void finalizedHindiApprovalCannotBeReplayed() {
        var p=automation.preview(new GenerateWordDocumentRequest("CONTRACT",41L,12L),"hi",auth);UUID id=UUID.fromString(p.get("generationId").toString());
        assertThatThrownBy(() -> automation.approve(id,Map.of("{{BORROWER_FULL_NAME}}","नाम 42"),false,auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        automation.approve(id,Map.of(),false,auth);
        assertThatThrownBy(() -> automation.approve(id,Map.of(),true,auth)).hasMessageContaining("already approved");
    }
    @Test void cacheDoesNotNormalizeSourceCaseOrWhitespace() {
        cache.convert(gatewayAudit,List.of(new TranslationCacheService.Field("{{A}}","RAMESH 42","TRANSLITERATE","personal_name")));
        cache.convert(gatewayAudit,List.of(new TranslationCacheService.Field("{{A}}","ramesh 42 ","TRANSLITERATE","personal_name")));
        assertThat(gateway.calls.get()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT source_value FROM document_translation_cache ORDER BY id",String.class)).containsExactly("RAMESH 42","ramesh 42 ");
    }
    @Test void successfulTranslationBatchSurvivesALaterProviderFailure() {
        var requested=java.util.stream.IntStream.range(0,26).mapToObj(i ->
            new TranslationCacheService.Field("{{FIELD_"+i+"}}","RAMESH 42","TRANSLITERATE","context_"+i)).toList();
        gateway.failAfter=1;
        assertThatThrownBy(() -> cache.convert(gatewayAudit,requested)).hasMessageContaining("failed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_translation_cache",Integer.class)).isEqualTo(25);
        gateway.failAfter=Integer.MAX_VALUE;
        var recovered=cache.convert(gatewayAudit,requested);
        assertThat(gateway.calls.get()).isEqualTo(5); // one success, three failed retries, one remaining cache miss
        assertThat(recovered.stream().filter(r -> Boolean.TRUE.equals(r.get("cacheHit"))).count()).isEqualTo(25);
    }
    @Test void deliveredVerificationQueriesExecuteReadOnly() throws Exception {
        String sql=Files.readString(Path.of("docs/document-enhancement-verification.sql"));
        assertThatCode(() -> jdbc.execute(sql)).doesNotThrowAnyException();
    }
    static class FakeGateway extends OpenAiTranslationGateway {
        AtomicInteger calls=new AtomicInteger();boolean fail;int failAfter=Integer.MAX_VALUE;int status=200;
        FakeGateway(AiUsageService usage){super(usage,"test-key-never-sent","gpt-5-mini",true);}
        @Override protected HttpResponse<String> send(HttpRequest request) throws Exception {
            assertThat(request.headers().allValues("Authorization")).containsExactly("Bearer test-key-never-sent");
            calls.incrementAndGet();if(fail||calls.get()>failAfter)throw new HttpTimeoutException("mock timeout");
            CompletableFuture<String> body=new CompletableFuture<>();var bytes=new java.io.ByteArrayOutputStream();
            request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<ByteBuffer>() {
                public void onSubscribe(java.util.concurrent.Flow.Subscription s){s.request(Long.MAX_VALUE);}
                public void onNext(ByteBuffer b){byte[] next=new byte[b.remaining()];b.get(next);bytes.writeBytes(next);}
                public void onError(Throwable e){body.completeExceptionally(e);}public void onComplete(){body.complete(bytes.toString(StandardCharsets.UTF_8));}
            });
            var json=DocumentJson.read(body.get());Map<String,String> output=new LinkedHashMap<>();
            assertThat(json.path("input").asText()).doesNotContain("C-41","16 Oct 2026");
            for(var item:DocumentJson.read(json.path("input").asText()))output.put(item.path("id").asText(),"रमेश 42");
            String result=status==401?DocumentJson.write(Map.of("error",Map.of("message","Rejected credential test-key-never-sent"))):DocumentJson.write(Map.of("status","completed","usage",Map.of("input_tokens",1000,"input_tokens_details",Map.of("cached_tokens",200),"output_tokens",100,"total_tokens",1100),"output",List.of(Map.of("content",List.of(Map.of("type","output_text","text",DocumentJson.write(output)))))));
            return new HttpResponse<>() {
                public int statusCode(){return status;}public HttpRequest request(){return request;}public Optional<HttpResponse<String>> previousResponse(){return Optional.empty();}
                public HttpHeaders headers(){return HttpHeaders.of(Map.of("x-request-id",List.of("mock-request")),(a,b)->true);}public String body(){return result;}
                public Optional<SSLSession> sslSession(){return Optional.empty();}public URI uri(){return request.uri();}public Version version(){return Version.HTTP_1_1;}
            };
        }
    }
}
