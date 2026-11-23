package dugar_lms_api.modules.wordtemplates.automation;

import dugar_lms_api.modules.reports.ReportAccessScope;
import dugar_lms_api.modules.wordtemplates.repository.WordDocumentGenerationRepository;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
public class DocumentUsageReportService {
    private final NamedParameterJdbcTemplate jdbc;
    public DocumentUsageReportService(NamedParameterJdbcTemplate jdbc) {this.jdbc=jdbc;}
    private record Query(String sql,MapSqlParameterSource params) {}
    private Query query(Map<String,String> filters,Authentication auth) {
        var scope=ReportAccessScope.from(auth);var p=new MapSqlParameterSource("fullAccess",scope.fullAccess()).addValue("scopeUserId",scope.userId());
        StringBuilder where=new StringBuilder(" WHERE ((g.contract_id IS NULL AND :fullAccess=TRUE) OR EXISTS(SELECT 1 FROM contracts c WHERE c.contract_id=g.contract_id AND "+WordDocumentGenerationRepository.ACCESS_FILTER+")) ");
        Map<String,String> columns=Map.ofEntries(Map.entry("username","COALESCE(u.username,g.username)"),Map.entry("module","COALESCE(u.module,'Document Generation')"),Map.entry("feature","u.feature"),
            Map.entry("documentType","g.template_name"),Map.entry("language","g.language_code"),Map.entry("contractNumber","g.contract_number"),
            Map.entry("model","u.model"),Map.entry("apiStatus","u.status"),Map.entry("generationStatus","g.status"));
        columns.forEach((key,column) -> {String value=filters.get(key);if(value!=null&&!value.isBlank()){where.append(" AND ").append(column).append(" ILIKE :").append(key);p.addValue(key,"%"+value+"%");}});
        if(filters.containsKey("fromDate")&&!filters.get("fromDate").isBlank()){where.append(" AND COALESCE(u.requested_at,g.started_at)>=CAST(:fromDate AS date)");p.addValue("fromDate",filters.get("fromDate"));}
        if(filters.containsKey("toDate")&&!filters.get("toDate").isBlank()){where.append(" AND COALESCE(u.requested_at,g.started_at)<CAST(:toDate AS date)+INTERVAL '1 day'");p.addValue("toDate",filters.get("toDate"));}
        return new Query("""
            SELECT g.id AS generation_id,g.batch_id,g.contract_id,g.contract_number,g.group_name,g.template_name,g.language_code,
                g.status AS generation_status,g.cache_hits,g.cache_misses,g.failure_reason,
                COALESCE(u.requested_at,g.started_at) AS occurred_at,COALESCE(u.username,g.username) AS username,
                u.id AS usage_id,u.provider_request_id,COALESCE(u.module,'Document Generation') AS module,u.feature,u.model,
                CASE WHEN u.id IS NULL THEN 0 ELSE u.input_tokens END AS input_tokens,
                CASE WHEN u.id IS NULL THEN 0 ELSE u.cached_input_tokens END AS cached_input_tokens,
                CASE WHEN u.id IS NULL THEN 0 ELSE u.output_tokens END AS output_tokens,
                CASE WHEN u.id IS NULL THEN 0 ELSE u.total_tokens END AS total_tokens,
                CASE WHEN u.id IS NULL THEN 0 ELSE u.estimated_usd END AS estimated_usd,
                CASE WHEN u.id IS NULL THEN 0 ELSE u.estimated_inr END AS estimated_inr,
                u.multiplier,CASE WHEN u.id IS NULL THEN 0 ELSE u.billable_inr END AS billable_inr,
                COALESCE(u.status,'NO_API') AS api_status,u.error_category,u.reconciled_usd
            FROM document_generation_audit g FULL JOIN ai_api_usage u ON u.generation_id=g.id
            """+where,p);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> report(Map<String,String> filters,Authentication auth,int page,int size) {
        Query q=query(filters,auth);size=Math.clamp(size,1,200);page=Math.max(page,0);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM ("+q.sql()+") r",q.params(),Long.class);
        var rows=jdbc.queryForList(q.sql()+" ORDER BY occurred_at DESC,usage_id DESC NULLS LAST LIMIT :limit OFFSET :offset",q.params().addValue("limit",size).addValue("offset",(long)page*size));
        var summary=jdbc.queryForMap("""
            WITH r AS (
            """+q.sql()+"""
            ), documents AS(SELECT DISTINCT generation_id,generation_status,cache_hits,cache_misses FROM r WHERE generation_id IS NOT NULL)
            SELECT (SELECT COUNT(*) FROM documents WHERE generation_status='SUCCESS') AS documents_generated,
                (SELECT COUNT(*) FROM documents WHERE generation_status='FAILED') AS failed_generations,
                (SELECT COALESCE(SUM(cache_hits),0) FROM documents) AS cache_hits,
                (SELECT COALESCE(SUM(cache_misses),0) FROM documents) AS cache_misses,
                COUNT(usage_id) AS api_requests,COUNT(usage_id) FILTER(WHERE api_status='FAILED') AS failed_requests,
                COALESCE(SUM(input_tokens),0) AS input_tokens,COALESCE(SUM(cached_input_tokens),0) AS cached_input_tokens,
                COALESCE(SUM(output_tokens),0) AS output_tokens,COALESCE(SUM(total_tokens),0) AS total_tokens,
                COALESCE(SUM(estimated_inr),0) AS estimated_inr,COALESCE(SUM(billable_inr),0) AS billable_inr,
                COUNT(usage_id) FILTER(WHERE estimated_inr IS NULL) AS requests_with_unknown_cost
            FROM r
            """,q.params());
        return Map.of("rows",rows,"total",total,"summary",summary,"page",page,"size",size);
    }
    public Map<String,Object> detail(UUID id,Authentication auth) {
        Query q=query(Map.of(),auth);var rows=jdbc.queryForList("SELECT * FROM ("+q.sql()+") r WHERE generation_id=:id",q.params().addValue("id",id));
        if(rows.isEmpty())throw new org.springframework.security.access.AccessDeniedException("Generation audit not accessible");
        var audit=jdbc.queryForMap("SELECT id,batch_id,contract_number,group_name,template_name,language_code,filename,output_format,username,started_at,completed_at,status,failure_reason,cache_hits,cache_misses,approved_by,approved_at FROM document_generation_audit WHERE id=:id",new MapSqlParameterSource("id",id));
        var versions=jdbc.queryForList("SELECT canonical_code,cache_id,cache_version FROM document_generation_translation_versions WHERE generation_id=:id",new MapSqlParameterSource("id",id));
        var totals=jdbc.queryForMap("""
            SELECT COALESCE(SUM(u.input_tokens*l.allocation),0) AS input_tokens,
                COALESCE(SUM(u.output_tokens*l.allocation),0) AS output_tokens,
                COALESCE(SUM(u.estimated_usd*l.allocation),0) AS estimated_usd,
                COALESCE(SUM(u.estimated_inr*l.allocation),0) AS estimated_inr,
                COALESCE(SUM(u.billable_inr*l.allocation),0) AS billable_inr
            FROM document_generation_api_links l JOIN ai_api_usage u ON u.id=l.usage_id WHERE l.generation_id=:id
            """,new MapSqlParameterSource("id",id));
        var linked=jdbc.queryForList("SELECT u.id,u.provider_request_id,u.model,u.input_tokens,u.cached_input_tokens,u.output_tokens,u.total_tokens,u.estimated_inr,u.billable_inr,u.status,l.allocation FROM document_generation_api_links l JOIN ai_api_usage u ON u.id=l.usage_id WHERE l.generation_id=:id",new MapSqlParameterSource("id",id));
        return Map.of("audit",audit,"requests",rows,"linkedRequests",linked,"translationVersions",versions,"totals",totals);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public byte[] excel(Map<String,String> filters,Authentication auth) {
        Query q=query(filters,auth);
        try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("AI usage");int[] index={0};List<String> headers=new ArrayList<>();
            jdbc.query(q.sql()+" ORDER BY occurred_at DESC,usage_id DESC NULLS LAST",q.params(), rs -> {
                if(headers.isEmpty()) {for(int c=1;c<=rs.getMetaData().getColumnCount();c++)headers.add(rs.getMetaData().getColumnLabel(c));var row=sheet.createRow(index[0]++);for(int c=0;c<headers.size();c++)row.createCell(c).setCellValue(headers.get(c));}
                var row=sheet.createRow(index[0]++);for(int c=0;c<headers.size();c++){Object value=rs.getObject(c+1);var cell=row.createCell(c);if(value instanceof Number n)cell.setCellValue(n.doubleValue());else cell.setCellValue(value==null?"":String.valueOf(value));}
            });
            var summary=book.createSheet("Filtered totals");var totals=(Map<?,?>)report(filters,auth,0,1).get("summary");int i=0;for(var e:totals.entrySet()){var row=summary.createRow(i++);row.createCell(0).setCellValue(e.getKey().toString());row.createCell(1).setCellValue(e.getValue().toString());}
            book.write(output);return output.toByteArray();
        }catch(java.io.IOException e){throw new IllegalArgumentException("Unable to export report",e);}
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public String printHtml(Map<String,String> filters,Authentication auth) {
        Query q=query(filters,auth);var rows=jdbc.queryForList(q.sql()+" ORDER BY occurred_at DESC,usage_id DESC NULLS LAST",q.params());
        StringBuilder html=new StringBuilder("<html><head><meta charset='utf-8'><title>AI API Usage &amp; Billing</title><style>@page{size:A3 landscape}body{font:10px sans-serif}table{border-collapse:collapse}td,th{border:1px solid #bbb;padding:3px}</style></head><body><h1>AI API Usage &amp; Billing</h1><p>Calculated estimates; unknown provider usage remains unpriced.</p><p>");
        html.append(escape(filters.toString())).append("</p><p>").append(escape(report(filters,auth,0,1).get("summary").toString())).append("</p><table><thead><tr>");
        List<String> columns=List.of("occurred_at","username","module","template_name","language_code","contract_number","model","input_tokens","cached_input_tokens","output_tokens","total_tokens","estimated_inr","multiplier","billable_inr","api_status","generation_status");
        columns.forEach(c -> html.append("<th>").append(escape(c)).append("</th>"));html.append("</tr></thead><tbody>");
        rows.forEach(r -> {html.append("<tr>");columns.forEach(c -> html.append("<td>").append(escape(r.get(c))).append("</td>"));html.append("</tr>");});
        return html.append("</tbody></table></body></html>").toString();
    }
    private String escape(Object value) {return value==null?"":value.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
