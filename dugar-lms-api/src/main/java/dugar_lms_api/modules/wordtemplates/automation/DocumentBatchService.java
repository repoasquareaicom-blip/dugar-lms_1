package dugar_lms_api.modules.wordtemplates.automation;

import dugar_lms_api.modules.wordtemplates.dto.GenerateWordDocumentRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.*;
import java.util.*;

@Service
public class DocumentBatchService {
    private final DocumentAutomationService automation;private final JdbcTemplate jdbc;private final TransactionTemplate transaction;
    public DocumentBatchService(DocumentAutomationService automation,JdbcTemplate jdbc,PlatformTransactionManager manager){this.automation=automation;this.jdbc=jdbc;this.transaction=new TransactionTemplate(manager);}
    @SuppressWarnings("unchecked")
    public Map<String,Object> preview(List<GenerateWordDocumentRequest> requests,Authentication auth) {
        if(requests==null||requests.isEmpty()||requests.size()>100)throw new IllegalArgumentException("Batch must contain between 1 and 100 documents");
        UUID batch=UUID.randomUUID();List<Map<String,Object>> results=new ArrayList<>();Map<Long,Set<UUID>> consumers=new HashMap<>();
        for(var request:requests) {
            if(request==null||request.contractId()==null||request.templateId()==null)throw new IllegalArgumentException("Every batch document needs a contract and template");
            try {
                var preview=automation.preview(request,request.languageCode(),auth,batch);results.add(preview);
                UUID generation=UUID.fromString(preview.get("generationId").toString());
                for(var field:(List<Map<String,Object>>)preview.get("fields"))if(field.get("originUsageId")!=null) {
                    long usage=((Number)field.get("originUsageId")).longValue();
                    Boolean createdInBatch=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ai_api_usage u JOIN document_generation_audit g ON g.id=u.generation_id WHERE u.id=? AND g.batch_id=?)",Boolean.class,usage,batch);
                    if(Boolean.TRUE.equals(createdInBatch))consumers.computeIfAbsent(usage,k -> new LinkedHashSet<>()).add(generation);
                }
            }catch(org.springframework.security.access.AccessDeniedException denied){results.add(Map.of("status","FAILED","reason","Contract not accessible"));}
            catch(IllegalArgumentException invalid){results.add(Map.of("status","FAILED","reason",invalid.getMessage()));}
        }
        transaction.executeWithoutResult(tx -> consumers.forEach((usage,ids) -> {
            UUID owner=jdbc.queryForObject("SELECT generation_id FROM ai_api_usage WHERE id=?",UUID.class,usage);ids.add(owner);
            BigDecimal share=BigDecimal.ONE.divide(BigDecimal.valueOf(ids.size()),10,RoundingMode.DOWN);BigDecimal remainder=BigDecimal.ONE;
            int index=0;for(UUID id:ids) {
                BigDecimal allocation=++index==ids.size()?remainder:share;remainder=remainder.subtract(allocation);
                jdbc.update("INSERT INTO document_generation_api_links(generation_id,usage_id,allocation) VALUES(?,?,?) ON CONFLICT(generation_id,usage_id) DO UPDATE SET allocation=EXCLUDED.allocation",id,usage,allocation);
            }
        }));
        return Map.of("batchId",batch,"documents",results);
    }
}
