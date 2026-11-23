package dugar_lms_api.modules.wordtemplates;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in integration tests against an isolated, disposable local database only. */
@EnabledIfEnvironmentVariable(named = "DOCUMENT_MENU_TEST_URL", matches = ".+")
class DocumentMenuMigrationTest {
    @TempDir Path migrations;
    private final String url = System.getenv("DOCUMENT_MENU_TEST_URL");

    private Flyway fixture(String variant, boolean includeOperationsRole) throws Exception {
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/document_menu_test")) {
            throw new IllegalArgumentException("Only a disposable local document_menu_test database is permitted");
        }
        Path source = Path.of("src/main/resources/db/migration");
        Files.copy(source.resolve("V000__create_core_lms_tables.sql"), migrations.resolve("V000__core.sql"));
        String operationsCode = variant.equals("canonical") ? "OPERATIONS" : "OPERATIONS_12";
        String transactionCode = switch (variant) {
            case "canonical" -> "OPERATIONS_TRANSACTIONS";
            case "singular" -> "OPERATIONS_TRANSACTION";
            default -> "OPERATIONS_TRANSACTION_18";
        };
        // Fixture data is itself installed by Flyway. IDs deliberately differ
        // from both application environments; unrelated grants must survive.
        Files.writeString(migrations.resolve("V066__fixture.sql"), """
            INSERT INTO roles(role_id,role_name,role_code) VALUES
                (701,'SUPER_ADMIN','SUPER_ADMIN'),(703,'Credit','CREDIT');
            %s
            INSERT INTO menus(menu_id,menu_name,parent_id,menu_code,url_path,menu_type) VALUES
                (901,'Administration',NULL,'%s','#','MODULE'),
                (902,'Masters',901,'ADMINISTRATION_MASTERS','#','MENU'),
                (903,'Operations',NULL,'%s','#','MODULE'),
                (904,'Transaction',903,'%s','#','MENU'),
                (905,'Word Template Master',902,'ADMINISTRATION_MASTERS_WORD_TEMPLATE_MASTER',
                    '/administration/masters/word-template-master','PAGE'),
                (906,'Generate Word Document',904,'OPERATIONS_TRANSACTIONS_GENERATE_WORD_DOCUMENT',
                    '/operations/transactions/generate-word-document','PAGE'),
                (907,'Unrelated page',903,'UNRELATED','/unrelated','PAGE');
            UPDATE menus SET is_active=FALSE,is_visible=FALSE WHERE menu_id IN (902,905,906);
            INSERT INTO role_permissions(role_id,menu_id,can_view,can_add,can_edit,can_delete) VALUES
                (701,905,FALSE,TRUE,TRUE,TRUE),(703,907,TRUE,TRUE,FALSE,TRUE),
                (703,906,FALSE,TRUE,FALSE,TRUE);
            """.formatted(includeOperationsRole
                ? "INSERT INTO roles(role_id,role_name,role_code) VALUES (702,'Operations','OPERATIONS');" : "",
                variant.equals("canonical") ? "ADMINISTRATION" : "ADMINISTRATION_11",
                operationsCode, transactionCode));
        if (variant.equals("legacy")) {
            Path fixture = migrations.resolve("V066__fixture.sql");
            Files.writeString(fixture, Files.readString(fixture)
                .replace("'SUPER_ADMIN','SUPER_ADMIN'", "'SUPER_ADMIN','SUPER_ADMIN_1'")
                .replace("'Operations','OPERATIONS'", "'Operations','OPERATIONS_2'"));
        }
        if (variant.equals("ambiguous")) {
            Files.writeString(migrations.resolve("V066__fixture.sql"), """
                INSERT INTO menus(menu_id,menu_name,parent_id,menu_code)
                VALUES (908,'Transactions',903,'TRANSACTIONS_908');
                """, java.nio.file.StandardOpenOption.APPEND);
        }
        Flyway before = flyway();
        before.clean();
        before.migrate();
        Files.copy(source.resolve("V067__rename_document_menus_and_restore_permissions.sql"),
            migrations.resolve("V067__rename_document_menus_and_restore_permissions.sql"));
        return flyway();
    }

    private Flyway flyway() {
        return Flyway.configure().dataSource(url, "postgres", "")
            .locations("filesystem:" + migrations.toAbsolutePath()).cleanDisabled(false).load();
    }

    private String scalar(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(url, "postgres", "");
             var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"canonical", "legacy", "singular"})
    void restoresOnlyRequiredAccessAndPreservesRoutesAndCrudFlags(String variant) throws Exception {
        Flyway flyway = fixture(variant, true);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(scalar("SELECT COUNT(*) FROM menus")).isEqualTo("7");
        assertThat(scalar("SELECT menu_name FROM menus WHERE menu_id=905")).isEqualTo("Document Template Master");
        assertThat(scalar("SELECT menu_name FROM menus WHERE menu_id=906")).isEqualTo("Generate Document");
        assertThat(scalar("SELECT parent_id FROM menus WHERE menu_id=905")).isEqualTo("902");
        assertThat(scalar("SELECT parent_id FROM menus WHERE menu_id=906")).isEqualTo("904");
        assertThat(scalar("SELECT url_path FROM menus WHERE menu_id=905"))
            .isEqualTo("/administration/masters/word-template-master");
        assertThat(scalar("SELECT url_path FROM menus WHERE menu_id=906"))
            .isEqualTo("/operations/transactions/generate-word-document");
        assertThat(scalar("SELECT COUNT(*) FROM menus WHERE is_active AND is_visible")).isEqualTo("7");
        assertThat(scalar("SELECT COUNT(*) FROM role_permissions WHERE role_id=701 AND can_view")).isEqualTo("6");
        assertThat(scalar("SELECT COUNT(*) FROM role_permissions WHERE role_id=702 AND can_view")).isEqualTo("3");
        assertThat(scalar("SELECT COUNT(*) FROM role_permissions WHERE role_id=702 AND menu_id IN (901,902,905)"))
            .isEqualTo("0");
        assertThat(scalar("SELECT can_view AND can_add AND can_edit AND can_delete FROM role_permissions WHERE role_id=701 AND menu_id=905"))
            .isEqualTo("t");
        assertThat(scalar("SELECT can_view AND can_add AND NOT can_edit AND can_delete FROM role_permissions WHERE role_id=703 AND menu_id=907"))
            .isEqualTo("t");
        assertThat(scalar("SELECT NOT can_view AND can_add AND NOT can_edit AND can_delete FROM role_permissions WHERE role_id=703 AND menu_id=906"))
            .isEqualTo("t");
    }

    @Test
    void missingRoleFailsWithoutPartiallyRenamingMenus() throws Exception {
        Flyway flyway = fixture("canonical", false);
        assertThatThrownBy(flyway::migrate).hasMessageContaining("SUPER_ADMIN and OPERATIONS roles");
        assertThat(scalar("SELECT menu_name FROM menus WHERE menu_id=905")).isEqualTo("Word Template Master");
        assertThat(scalar("SELECT COUNT(*) FROM role_permissions")).isEqualTo("3");
    }

    @Test
    void ambiguousLegacyHierarchyFailsWithoutPartialChanges() throws Exception {
        Flyway flyway = fixture("ambiguous", true);
        assertThatThrownBy(flyway::migrate).hasMessageContaining("more than one row");
        assertThat(scalar("SELECT menu_name FROM menus WHERE menu_id=906")).isEqualTo("Generate Word Document");
        assertThat(scalar("SELECT COUNT(*) FROM role_permissions")).isEqualTo("3");
    }
}
