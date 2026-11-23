package dugar_lms_api.modules.wordtemplates.automation;

import dugar_lms_api.modules.reports.ReportAccessScope;
import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.wordtemplates.dto.WordTemplateDto;
import dugar_lms_api.modules.wordtemplates.repository.WordDocumentGenerationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.*;

/** The procedure and ALL function reads run inside one Spring-bound JDBC transaction.
 * No calculation connection is held while rendering or calling the translation provider. */
@Service
public class ReminderLetterService {
    public static final Set<String> CODES=Set.of("BORROWER_NAME","BORROWER_SWD","VEHICLE","EMI","CURRENT_DUE",
        "NO_OF_OVERDUES","TOTAL_OS","TOTAL_OS_WORDS","GUARANTOR_1_NAME","GUARANTOR_2_NAME","BORROWER_ADDRESS","CONTRACT_NUMBER","CONTRACT_DATE");
    private static final String ACCESS=WordDocumentGenerationRepository.ACCESS_FILTER;
    private static final String ELIGIBLE="c.is_active IS TRUE AND c.is_draft IS FALSE AND UPPER(TRIM(c.status))='Y' AND c.loan_close_date IS NULL";
    private static final String MATCH=" AND (NOT :pending OR (r.document_values->>'CURRENT_DUE')::numeric>0)"
        +" AND (:keyword='' OR POSITION(LOWER(:keyword) IN LOWER(r.contract_number))>0 OR POSITION(LOWER(:keyword) IN LOWER(r.document_values->>'BORROWER_NAME'))>0)";
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final TransactionTemplate calculation;
    private final MenuAccessService access;
    private final int limit;
    public ReminderLetterService(JdbcTemplate jdbc,PlatformTransactionManager manager,MenuAccessService access,
            @Value("${app.document-exports.max-contracts:100000}") int limit) {
        this.jdbc=jdbc;this.named=new NamedParameterJdbcTemplate(jdbc);this.access=access;this.limit=limit;
        calculation=new TransactionTemplate(manager);
        calculation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        calculation.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        calculation.setTimeout(600);
    }
    public static boolean isReminder(WordTemplateDto template) {
        return List.of(String.valueOf(template.documentKey()),String.valueOf(template.templateName())).stream()
            .anyMatch(v -> v.trim().replace('_',' ').equalsIgnoreCase("Reminder Letter"));
    }
    private MapSqlParameterSource scope(Authentication auth) {
        Long id=DocumentAutomationService.userId(auth);
        String group=jdbc.queryForObject("SELECT user_group FROM users WHERE user_id=? AND is_active",String.class,id);
        var scope=new ReportAccessScope(group!=null&&group.trim().equalsIgnoreCase("admin"),id,group);
        return new MapSqlParameterSource("fullAccess",scope.fullAccess()).addValue("scopeUserId",scope.userId());
    }
    private void require(Authentication auth) {
        if(auth==null||DocumentAutomationService.userId(auth)==null||!access.canView(DocumentAutomationController.GENERATE,auth))
            throw new AccessDeniedException("Generate Document permission required");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE user_id=? AND is_active)",Boolean.class,DocumentAutomationService.userId(auth))))
            throw new AccessDeniedException("User is inactive");
    }
    private void calculate(LocalDate date,Runnable reads) {
        calculation.executeWithoutResult(tx -> {
            jdbc.update("CALL public.sp_branch_wise_ageing_test_2(?, NULL::varchar)",date);
            reads.run();
            // The unchanged procedure uses ON COMMIT PRESERVE ROWS. Clean only its session-local tables.
            jdbc.execute("DROP TABLE IF EXISTS pg_temp.tmp_contract_report,pg_temp.tmp_branch_wise_ageing,pg_temp.tmp_contract_aum,"
                +"pg_temp.tmp_contract_ageing,pg_temp.tmp_emi_outstanding,pg_temp.tmp_emi_schedule,pg_temp.tmp_contract_collection,pg_temp.tmp_active_contracts");
        });
    }
    public Map<String,Object> snapshot(LocalDate date,Authentication auth) {
        require(auth);if(date==null)throw new IllegalArgumentException("As-on Date is required");
        UUID id=UUID.randomUUID();
        calculate(date,() -> {
            jdbc.update("INSERT INTO document_reminder_snapshots(id,user_id,as_on_date) VALUES(?,?,?)",id,DocumentAutomationService.userId(auth),date);
            int count=named.update("INSERT INTO document_reminder_rows(snapshot_id,contract_id,contract_number,document_values) "
                +"SELECT :id,c.contract_id,c.contract_number,(SELECT jsonb_object_agg(key,value) FROM jsonb_each_text(to_jsonb(f))) FROM public.contracts c CROSS JOIN LATERAL public.fn_doc_reminder_letter(c.contract_id,:date) f "
                +"WHERE "+ELIGIBLE+" AND "+ACCESS+" ORDER BY c.contract_id LIMIT :limit",
                scope(auth).addValue("id",id).addValue("date",date).addValue("limit",limit+1));
            if(count>limit)throw new IllegalArgumentException("Reminder selection exceeds configured contract limit");
        });
        return Map.of("id",id,"asOnDate",date.toString());
    }
    private void owned(UUID id,LocalDate date,Authentication auth) {
        require(auth);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM document_reminder_snapshots WHERE id=? AND user_id=? AND as_on_date=? AND expires_at>CURRENT_TIMESTAMP)",Boolean.class,id,DocumentAutomationService.userId(auth),date)))
            throw new AccessDeniedException("Reminder calculation expired or inaccessible; refresh the grid");
    }
    public Map<String,Object> rows(UUID id,LocalDate date,boolean pending,String keyword,int page,int size,Authentication auth) {
        owned(id,date,auth);size=Math.max(1,Math.min(250,size));page=Math.max(0,page);
        var params=scope(auth).addValue("id",id).addValue("pending",pending).addValue("keyword",keyword==null?"":keyword.trim());
        String from=" FROM document_reminder_rows r JOIN public.contracts c ON c.contract_id=r.contract_id WHERE r.snapshot_id=:id AND "+ELIGIBLE+" AND "+ACCESS+MATCH;
        Long total=named.queryForObject("SELECT COUNT(*)"+from,params,Long.class);
        var content=named.queryForList("SELECT r.contract_id AS \"contractId\",r.contract_number AS \"contractNumber\","
            +"r.document_values->>'BORROWER_NAME' AS \"borrowerName\",(r.document_values->>'EMI')::numeric AS emi,"
            +"(r.document_values->>'CURRENT_DUE')::numeric AS \"currentDue\",(r.document_values->>'NO_OF_OVERDUES')::integer AS \"noOfOverdues\","
            +"(r.document_values->>'TOTAL_OS')::numeric AS \"totalOs\""+from+" ORDER BY r.contract_id LIMIT :size OFFSET :offset",params.addValue("size",size).addValue("offset",(long)page*size));
        return Map.of("content",content,"totalElements",total==null?0:total);
    }
    /** Called within job submission; take only authorized rows in the displayed calculation/filter. */
    public void select(UUID job,DocumentExportRequest request,Authentication auth) {
        if(request.asOnDate()==null||request.reminderSnapshotId()==null)throw new IllegalArgumentException("Calculate Reminder Letter before generating");
        owned(request.reminderSnapshotId(),request.asOnDate(),auth);
        var params=scope(auth).addValue("job",job).addValue("id",request.reminderSnapshotId()).addValue("pending",request.pendingOnly())
            .addValue("keyword",request.search()==null?"":request.search().trim());
        String ids="";
        if(!request.allMatching()) {
            if(request.contractIds()==null||request.contractIds().isEmpty()||request.contractIds().size()>limit||request.contractIds().stream().anyMatch(v -> v==null||v<1))
                throw new IllegalArgumentException("Select valid contracts within the configured export limit");
            params.addValue("ids",request.contractIds());ids=" AND r.contract_id IN (:ids)";
        }
        int selected=named.update("INSERT INTO document_export_items(job_id,contract_id,contract_number) SELECT :job,r.contract_id,r.contract_number"
            +" FROM document_reminder_rows r JOIN public.contracts c ON c.contract_id=r.contract_id WHERE r.snapshot_id=:id AND "+ELIGIBLE+" AND "+ACCESS+MATCH+ids,params);
        if(!request.allMatching()&&selected!=new HashSet<>(request.contractIds()).size())throw new AccessDeniedException("Selection no longer matches authorized Reminder Letter rows");
    }
    /** Exactly one procedure call per export batch; materialize all 13 values on that connection. */
    public void prepareExport(UUID job,LocalDate date,Authentication auth) {
        require(auth);if(date==null)throw new IllegalArgumentException("As-on Date is required");
        calculate(date,() -> named.update("UPDATE document_export_items i SET document_values=(SELECT jsonb_object_agg(key,value) FROM jsonb_each_text(to_jsonb(f))) FROM public.contracts c "
            +"CROSS JOIN LATERAL public.fn_doc_reminder_letter(c.contract_id,:date) f WHERE i.job_id=:job AND i.contract_id=c.contract_id AND "+ELIGIBLE+" AND "+ACCESS,
            scope(auth).addValue("job",job).addValue("date",date)));
    }
    public static Map<String,String> values(Object json) {
        if(json==null)throw new IllegalArgumentException("Reminder financial values are unavailable for this contract");
        var raw=DocumentJson.map(json.toString());Map<String,String> values=new LinkedHashMap<>();
        for(String code:CODES) {
            if(!raw.containsKey(code))throw new IllegalArgumentException("Reminder function must return all 13 placeholders");
            values.put("{{"+code+"}}",Objects.toString(raw.get(code),""));
        }
        return values;
    }
    public void cleanup() {
        jdbc.update("DELETE FROM document_reminder_snapshots WHERE expires_at<CURRENT_TIMESTAMP");
        jdbc.update("UPDATE document_export_items i SET document_values=NULL FROM document_export_jobs j WHERE i.job_id=j.id AND j.expires_at<CURRENT_TIMESTAMP AND j.status NOT IN ('QUEUED','RUNNING') AND i.document_values IS NOT NULL");
    }
}
