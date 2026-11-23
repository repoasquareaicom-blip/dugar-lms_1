package dugar_lms_api.modules.wordtemplates.automation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;

public final class DocumentJson {
    public static final ObjectMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder()
        .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private DocumentJson() {}
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid document metadata", e); }
    }
    public static JsonNode read(String value) {
        try { return MAPPER.readTree(value); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid JSON response", e); }
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> map(String value) { return MAPPER.convertValue(read(value), Map.class); }
}
