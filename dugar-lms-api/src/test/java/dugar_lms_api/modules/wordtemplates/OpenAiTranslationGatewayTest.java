package dugar_lms_api.modules.wordtemplates;

import dugar_lms_api.modules.wordtemplates.automation.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenAiTranslationGatewayTest {
    static class Transport extends OpenAiTranslationGateway {
        int calls;String response;int firstStatus=200;boolean timeout;
        Transport(AiUsageService usage,String response){super(usage,"mock-not-sent","gpt-5-mini",true);this.response=response;}
        @Override protected HttpResponse<String> send(HttpRequest request) throws Exception {
            calls++;if(timeout)throw new HttpTimeoutException("mock");
            @SuppressWarnings("unchecked") HttpResponse<String> http=mock(HttpResponse.class);
            when(http.statusCode()).thenReturn(calls==1?firstStatus:200);
            when(http.body()).thenReturn(response);when(http.headers()).thenReturn(HttpHeaders.of(Map.of("x-request-id",List.of("req-test")),(a,b)->true));return http;
        }
    }
    private AiUsageService usage() {var usage=mock(AiUsageService.class);when(usage.pricing("gpt-5-mini")).thenReturn(Map.of());return usage;}
    private String completed(String text) {return DocumentJson.write(Map.of("status","completed","output",List.of(Map.of("content",List.of(Map.of("type","output_text","text",text))))));}
    private List<OpenAiTranslationGateway.Item> items(){return List.of(new OpenAiTranslationGateway.Item("f","RAMESH 42","TRANSLITERATE","personal_name"));}
    @ParameterizedTest @ValueSource(strings={"not-json","{}","{\"f\":\"English 42\"}","{\"f\":\"रमेश English 42\"}","{\"f\":\"रमेश 43\"}","{\"f\":null}"})
    void rejectsMalformedMissingOrUnsafeFields(String text) {
        var usage=usage();var gateway=new Transport(usage,completed(text));
        assertThatThrownBy(() -> gateway.translate(UUID.randomUUID(),items())).hasMessageContaining("failed");
        assertThat(gateway.calls).isEqualTo(1);verify(usage).record(any(UUID.class),eq("Document Generation"),eq("Hindi conversion"),eq("gpt-5-mini"),any(),eq("req-test"),isNull(),isNull(),isNull(),isNull(),eq("FAILED"),eq("INVALID_RESPONSE"),anyMap());
    }
    @Test void incompleteResponseIsNeverAccepted() {
        var gateway=new Transport(usage(),"{\"status\":\"incomplete\",\"output\":[]}");
        assertThatThrownBy(() -> gateway.translate(UUID.randomUUID(),items())).hasMessageContaining("INCOMPLETE_RESPONSE");
    }
    @Test void rateLimitRetriesAreBoundedAndEveryAttemptIsLogged() {
        var usage=usage();var gateway=new Transport(usage,completed("{\"f\":\"रमेश 42\"}"));gateway.firstStatus=429;
        assertThat(gateway.translate(UUID.randomUUID(),items()).values()).containsEntry("f","रमेश 42");
        assertThat(gateway.calls).isEqualTo(2);verify(usage,times(2)).record(any(UUID.class),anyString(),anyString(),anyString(),any(),any(),any(),any(),any(),any(),anyString(),any(),anyMap());
    }
    @Test void timeoutsStopAfterThreeAttempts() {
        var gateway=new Transport(usage(),"");gateway.timeout=true;
        assertThatThrownBy(() -> gateway.translate(UUID.randomUUID(),items())).hasMessageContaining("NETWORK_ERROR");assertThat(gateway.calls).isEqualTo(3);
    }
}
