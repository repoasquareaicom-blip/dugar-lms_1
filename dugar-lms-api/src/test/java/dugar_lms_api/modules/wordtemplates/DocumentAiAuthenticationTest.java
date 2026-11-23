package dugar_lms_api.modules.wordtemplates.automation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import java.io.Reader;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Fictional credentials and mocked transport only; no HTTP is sent. */
class DocumentAiAuthenticationTest {
    private static final String TEST_KEY="test-only-credential";
    private final AiUsageService usage=mock(AiUsageService.class);
    private final List<OpenAiTranslationGateway.Item> items=List.of(new OpenAiTranslationGateway.Item("name","Ramesh 42","TRANSLITERATE","personal_name"));

    private AnnotationConfigApplicationContext context(Map<String,Object> environment) throws Exception {
        Properties file=new Properties();
        try(Reader reader=Files.newBufferedReader(Path.of("src/main/resources/application.properties"))){file.load(reader);}
        assertThat("${OPENAI_API_KEY:}".equals(file.getProperty("app.document-ai.api-key")))
            .as("API key configuration must be an environment-only reference (value omitted)").isTrue();
        var context=new AnnotationConfigApplicationContext();
        var sources=context.getEnvironment().getPropertySources();
        // Never read the test runner's actual credentials or datasource.
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addFirst(new SystemEnvironmentPropertySource("mockEnvironment",environment));
        sources.addLast(new MapPropertySource("documentProperties",Map.of(
            "app.document-ai.api-key",file.getProperty("app.document-ai.api-key"),
            "app.document-ai.model",file.getProperty("app.document-ai.model"),"app.document-ai.enabled","true")));
        context.registerBean(AiUsageService.class,()->usage);
        context.registerBean(OpenAiTranslationGateway.class);
        context.refresh();return context;
    }
    private OpenAiTranslationGateway mockedTransport(OpenAiTranslationGateway configured,int status) throws Exception {
        var gateway=spy(configured);
        when(usage.pricing("gpt-5-mini")).thenReturn(Map.of());
        @SuppressWarnings("unchecked") HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(),(a,b)->true));
        when(response.body()).thenReturn(status==200?DocumentJson.write(Map.of("status","completed","output",List.of(Map.of("content",List.of(Map.of(
            "type","output_text","text",DocumentJson.write(Map.of("name","\u0930\u092e\u0947\u0936 42"))))))))
            :DocumentJson.write(Map.of("error",Map.of("message","Rejected credential "+TEST_KEY))));
        doReturn(response).when(gateway).send(any(HttpRequest.class));return gateway;
    }
    @Test void environmentKeyBindsToExistingGatewayAndCorrectBearerHeader() throws Exception {
        try(var context=context(Map.of("OPENAI_API_KEY",TEST_KEY))){
            var gateway=mockedTransport(context.getBean(OpenAiTranslationGateway.class),200);
            assertThat(gateway.translate(UUID.randomUUID(),items).values()).containsEntry("name","\u0930\u092e\u0947\u0936 42");
            var sent=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(gateway).send(sent.capture());
            assertThat(sent.getValue().headers().allValues("Authorization")).containsExactly("Bearer "+TEST_KEY);
            assertThat(sent.getValue().uri()).isEqualTo(URI.create("https://api.openai.com/v1/responses"));
            assertThat(sent.getValue().method()).isEqualTo("POST");
            assertThat(sent.getValue().headers().firstValue("Content-Type")).contains("application/json");
            assertThat(sent.getValue().headers().firstValue("OpenAI-Organization")).isEmpty();
            assertThat(sent.getValue().headers().firstValue("OpenAI-Project")).isEmpty();
            assertThat(gateway.model()).isEqualTo("gpt-5-mini");
        }
    }
    @Test void directSpringEnvironmentOverrideTakesPrecedence() throws Exception {
        try(var context=context(Map.of("OPENAI_API_KEY",TEST_KEY,"APP_DOCUMENT_AI_API_KEY","test-direct-override"))){
            var gateway=mockedTransport(context.getBean(OpenAiTranslationGateway.class),200);
            gateway.translate(UUID.randomUUID(),items);
            verify(gateway).send(argThat(request->request.headers().firstValue("Authorization").orElse("").equals("Bearer test-direct-override")));
        }
    }
    @Test void surroundingWhitespaceIsRemovedFromTheHeader() throws Exception {
        try(var context=context(Map.of("OPENAI_API_KEY"," \t"+TEST_KEY+"\r\n"))){
            var gateway=mockedTransport(context.getBean(OpenAiTranslationGateway.class),200);
            gateway.translate(UUID.randomUUID(),items);
            verify(gateway).send(argThat(request->request.headers().firstValue("Authorization").orElse("").equals("Bearer "+TEST_KEY)));
        }
    }
    @Test void missingKeyAllowsStartupAndWorkWithoutConversionButBlocksHindi() throws Exception {
        try(var context=context(Map.of())){
            var gateway=mockedTransport(context.getBean(OpenAiTranslationGateway.class),200);
            assertThat(gateway.translate(UUID.randomUUID(),List.of()).values()).isEmpty();
            assertThatThrownBy(()->gateway.translate(UUID.randomUUID(),items)).hasMessageContaining("OPENAI_API_KEY");
            verify(gateway,never()).send(any());verifyNoInteractions(usage);
        }
    }
    @ParameterizedTest @ValueSource(strings={"\"test-only-credential\"","'test-only-credential'","test embedded space","test\nheader","${UNRESOLVED_SECRET}"})
    void malformedCredentialsFailBeforeTransportWithoutEchoingValues(String key) throws Exception {
        try(var context=context(Map.of("OPENAI_API_KEY",key))){
            var gateway=mockedTransport(context.getBean(OpenAiTranslationGateway.class),200);
            assertThatThrownBy(()->gateway.translate(UUID.randomUUID(),items)).hasMessageContaining("configuration is malformed").hasMessageNotContaining(key);
            verify(gateway,never()).send(any());verifyNoInteractions(usage);
        }
    }
    @Test void unauthorizedIsNotRetriedAndNeverExposesCredentialOrProviderBody() throws Exception {
        try(var context=context(Map.of("OPENAI_API_KEY",TEST_KEY))){
            var gateway=mockedTransport(context.getBean(OpenAiTranslationGateway.class),401);
            assertThatThrownBy(()->gateway.translate(UUID.randomUUID(),items)).hasMessageContaining("HTTP_401","OPENAI_API_KEY","restart")
                .hasMessageNotContaining(TEST_KEY).hasMessageNotContaining("Rejected credential");
            verify(gateway).send(any());
            verify(usage).record(any(),anyString(),anyString(),eq("gpt-5-mini"),any(),isNull(),isNull(),isNull(),isNull(),isNull(),eq("FAILED"),eq("HTTP_401"),anyMap());
        }
    }
}
