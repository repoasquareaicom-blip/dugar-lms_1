package dugar_lms_api.modules.wordtemplates;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.wordtemplates.automation.*;
import dugar_lms_api.modules.wordtemplates.dto.GenerateWordDocumentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentAutomationAuthorizationTest {
    private final MenuAccessService access=mock(MenuAccessService.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final DocumentFieldService fields=mock(DocumentFieldService.class);
    private final DocumentAutomationService generation=mock(DocumentAutomationService.class);
    private final TranslationCacheService cache=mock(TranslationCacheService.class);
    private final DocumentUsageReportService reports=mock(DocumentUsageReportService.class);
    private final Authentication user=mock(Authentication.class);
    private final DocumentAutomationController controller=new DocumentAutomationController(access,jdbc,fields,generation,cache,reports);

    @Test void reminderGridEndpointBindsIsoDateAndReportsInvalidDatesAndDeniedAccess() throws Exception {
        var reminder=mock(ReminderLetterService.class);
        var exports=new DocumentExportController(mock(DocumentExportService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(exports,"reminders",reminder);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(exports).build();
        var id=java.util.UUID.randomUUID();var date=java.time.LocalDate.of(2026,10,17);
        when(reminder.rows(eq(id),eq(date),eq(true),eq(""),eq(0),eq(25),any())).thenReturn(Map.of("content",java.util.List.of(),"totalElements",0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/document-exports/reminder/snapshots/"+id).param("asOnDate","2026-10-17").principal(user))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(reminder).rows(id,date,true,"",0,25,user);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/document-exports/reminder/snapshots/"+id).param("asOnDate","invalid").principal(user))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        when(reminder.rows(any(),any(),anyBoolean(),anyString(),anyInt(),anyInt(),any())).thenThrow(new AccessDeniedException("Not accessible"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/document-exports/reminder/snapshots/"+id).param("asOnDate","2026-10-17").principal(user))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
    @Test void contractFieldPreviewRequiresMasterPermission() {
        when(access.canView(DocumentAutomationController.GENERATE,user)).thenReturn(true);
        assertThatThrownBy(() -> controller.contractFields("C-41",user)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(generation);
        when(access.canView(DocumentAutomationController.MASTER,user)).thenReturn(true);
        controller.contractFields("C-41",user);
        verify(generation).contractFieldValues("C-41",user);
    }
    @Test void generationPermissionDoesNotGrantPricingOrCacheAdministration() {
        when(access.canView(DocumentAutomationController.GENERATE,user)).thenReturn(true);
        assertThatThrownBy(() -> controller.cache("",0,user)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc,fields,generation,cache,reports);
    }
    @Test void explicitReportPermissionIsReadOnlyAndDoesNotGrantAliasManagement() {
        when(access.canView(DocumentAutomationController.REPORT,user)).thenReturn(true);
        when(reports.report(Map.of(),user,0,50)).thenReturn(Map.of());
        controller.report(Map.of(),0,50,user);
        verify(reports).report(Map.of(),user,0,50);
        assertThatThrownBy(() -> controller.alias(new DocumentAutomationController.AliasRequest(null,"name","{{BORROWER_FULL_NAME}}"),user)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc,fields,cache);
    }
    @Test void deniedGenerationNeverFetchesContractOrCallsTheProvider() {
        assertThatThrownBy(() -> controller.preview(new GenerateWordDocumentRequest("CONTRACT",41L,12L),user)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc,fields,generation,cache,reports);
    }
}
