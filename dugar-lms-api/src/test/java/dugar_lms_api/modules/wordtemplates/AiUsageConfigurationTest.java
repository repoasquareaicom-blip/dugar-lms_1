package dugar_lms_api.modules.wordtemplates;

import dugar_lms_api.modules.wordtemplates.automation.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiUsageConfigurationTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private AiUsageService usage(String fx) {
        return new AiUsageService(jdbc,manager,new BigDecimal("0.25"),new BigDecimal("0.025"),new BigDecimal("2"),new BigDecimal(fx));
    }
    @Test void backendPricingNeedsNoActiveDatabasePricingVersionAndAlwaysBillsThreeTimes() {
        var rates=usage("84").pricing("gpt-5-mini");
        assertThat(rates).containsEntry("multiplier",BigDecimal.valueOf(3)).containsEntry("usd_inr",new BigDecimal("84"));
        var usd=AiUsageService.cost(1000,200,100,(BigDecimal)rates.get("input_per_million"),(BigDecimal)rates.get("cached_input_per_million"),(BigDecimal)rates.get("output_per_million"));
        assertThat(usd.multiply((BigDecimal)rates.get("usd_inr")).multiply((BigDecimal)rates.get("multiplier"))).isEqualByComparingTo("0.10206");
        verifyNoInteractions(jdbc,manager);
    }
    @Test void missingExchangeRateBlocksProviderTransport() {
        var gateway=new OpenAiTranslationGateway(usage("0"),"mock-key-never-sent","gpt-5-mini",true) {
            @Override protected java.net.http.HttpResponse<String> send(java.net.http.HttpRequest request) {
                throw new AssertionError("Provider must not be called with invalid pricing");
            }
        };
        assertThatThrownBy(() -> gateway.translate(UUID.randomUUID(),List.of(new OpenAiTranslationGateway.Item("name","Ramesh","TRANSLITERATE","personal_name")))).hasMessageContaining("exchange rate");
        verifyNoInteractions(jdbc,manager);
    }
    @Test void anotherModelCannotSilentlyUseMiniRates() {
        assertThatThrownBy(() -> usage("84").pricing("another-model")).hasMessageContaining("gpt-5-mini");
        assertThatThrownBy(() -> new OpenAiTranslationGateway(usage("84"),"","another-model",false)).hasMessageContaining("gpt-5-mini");
    }
}
