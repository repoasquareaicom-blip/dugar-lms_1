package dugar_lms_api.modules.wordtemplates.automation;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.contracts.repository.ContractListRepository;
import dugar_lms_api.modules.contracts.service.*;
import dugar_lms_api.modules.reports.ReportAccessScope;
import dugar_lms_api.modules.wordtemplates.dto.GenerateWordDocumentRequest;
import dugar_lms_api.modules.wordtemplates.repository.*;
import dugar_lms_api.modules.wordtemplates.service.WordTemplateStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Durable selection/progress; bounded workers and disk-backed artifacts keep HTTP requests short. */
@Service
public class DocumentExportService {
    @org.springframework.beans.factory.annotation.Autowired
    private ReminderLetterService reminders;
    private final JdbcTemplate jdbc; private final DocumentAutomationService automation;
    private final WordTemplateRepository templates; private final WordTemplateStorageService storage;
    private final WordDocumentGenerationRepository contracts; private final ContractListRepository lists;
    private final ContractListService listService; private final MenuAccessService access;
    private final DocumentPdfConverter pdf; private final TransactionTemplate transaction;
    private final Path root; private final long maxBytes; private final int maxContracts;
    private final ThreadPoolExecutor workers;
    private final Set<UUID> activeJobs=ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService janitor=Executors.newSingleThreadScheduledExecutor(r -> {var t=new Thread(r,"document-export-cleanup");t.setDaemon(true);return t;});
    public DocumentExportService(JdbcTemplate jdbc,DocumentAutomationService automation,WordTemplateRepository templates,
            WordTemplateStorageService storage,WordDocumentGenerationRepository contracts,ContractListRepository lists,
            ContractListService listService,MenuAccessService access,DocumentPdfConverter pdf,PlatformTransactionManager manager,
            @Value("${app.document-exports.storage-dir:./storage/document-exports}") String directory,
            @Value("${app.document-exports.max-bytes:1073741824}") long maxBytes,
            @Value("${app.document-exports.max-contracts:100000}") int maxContracts) {
        this.jdbc=jdbc;this.automation=automation;this.templates=templates;this.storage=storage;this.contracts=contracts;
        this.lists=lists;this.listService=listService;this.access=access;this.pdf=pdf;this.transaction=new TransactionTemplate(manager);
        if(maxBytes<1||maxContracts<1)throw new IllegalArgumentException("Export limits must be positive");
        this.root=Path.of(directory).toAbsolutePath().normalize();this.maxBytes=maxBytes;this.maxContracts=maxContracts;
        workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r -> {var t=new Thread(r,"document-export");t.setDaemon(true);return t;});
    }
    @EventListener(ApplicationReadyEvent.class) public void startCleanup() {
        janitor.scheduleWithFixedDelay(() -> {try{cleanup();}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Document export cleanup failed: {}",e.getClass().getSimpleName());}},0,60,TimeUnit.SECONDS);
    }
    @PreDestroy public void close(){
        janitor.shutdownNow();workers.shutdownNow();
        for(UUID id:activeJobs)jdbc.update("UPDATE document_export_jobs SET lease_until=CURRENT_TIMESTAMP WHERE id=? AND status IN ('QUEUED','RUNNING')",id);
        cleanup();
    }
    private Authentication currentUser(Long id) {
        if(id==null)throw new AccessDeniedException("Authentication required");
        var rows=jdbc.queryForList("SELECT username,user_group FROM users WHERE user_id=? AND is_active",id);
        if(rows.isEmpty())throw new AccessDeniedException("User is inactive");
        var row=rows.getFirst();var auth=new UsernamePasswordAuthenticationToken(row.get("username").toString(),"",List.of());
        var details=new HashMap<String,Object>();details.put("userId",id);details.put("userGroup",row.get("user_group"));auth.setDetails(details);
        require(auth);return auth;
    }
    private void require(Authentication auth) {
        if(auth==null||DocumentAutomationService.userId(auth)==null||!access.canView(DocumentAutomationController.GENERATE,auth))throw new AccessDeniedException("Generate Document permission required");
    }
    private ContractListCriteria criteria(ContractListCriteria c) {
        if(c==null)c=new ContractListCriteria(null,null,null,null,null,null,null,null,null,false,null,0,250,"contractDate","desc");
        return listService.validatedCriteria(new ContractListCriteria(c.keyword(),c.branch(),c.status(),c.product(),c.customerName(),c.contractDateFrom(),c.contractDateTo(),c.minimumLoanAmount(),c.maximumLoanAmount(),false,null,0,250,c.sortColumn(),c.sortDirection()));
    }
    public Map<String,Object> submit(DocumentExportRequest request,Authentication auth) {
        require(auth);auth=currentUser(DocumentAutomationService.userId(auth));final Authentication owner=auth;
        if(request==null||request.templateId()==null||!Set.of("DOCX","PDF").contains(String.valueOf(request.outputFormat())))throw new IllegalArgumentException("Select a document and Word or PDF output");
        UUID id=UUID.randomUUID();
        transaction.executeWithoutResult(tx -> {
            jdbc.queryForList("SELECT id FROM word_templates WHERE id=? FOR SHARE",request.templateId());
            var template=templates.findTemplate(request.templateId()).orElseThrow(() -> new IllegalArgumentException("Document not found"));
            if(!Boolean.TRUE.equals(template.isActive()))throw new IllegalArgumentException("Document is inactive");
            var file=templates.findTemplateFile(template.id()).orElseThrow();
            storage.validateStored(file.storagePath());
            String hash=fingerprint(storage.path(file.storagePath()));
            jdbc.update("INSERT INTO document_export_jobs(id,template_id,template_name,language_code,template_hash,output_format,all_matching,selection,user_id,username) VALUES(?,?,?,?,?,?,?,?::jsonb,?,?)",
                id,template.id(),template.templateName(),template.languageCode(),hash,request.outputFormat(),request.allMatching(),DocumentJson.write(request),DocumentAutomationService.userId(owner),owner.getName());
            if(ReminderLetterService.isReminder(template))reminders.select(id,request,owner);
            else if(request.allMatching())lists.snapshotDocumentSelection(id,criteria(request.criteria()),ReportAccessScope.from(owner));
            else {
                if(request.contractIds()==null||request.contractIds().isEmpty()||request.contractIds().size()>maxContracts||request.contractIds().stream().anyMatch(v -> v==null||v<1))throw new IllegalArgumentException("Select valid contracts within the configured export limit");
                jdbc.update("INSERT INTO document_export_items(job_id,contract_id) SELECT ?,value::bigint FROM jsonb_array_elements_text(?::jsonb) ON CONFLICT DO NOTHING",id,DocumentJson.write(request.contractIds()));
            }
            Long total=jdbc.queryForObject("SELECT COUNT(*) FROM document_export_items WHERE job_id=?",Long.class,id);
            if(total==null||total==0)throw new IllegalArgumentException("No authorized matching contracts");
            if(total>maxContracts)throw new IllegalArgumentException("Selection exceeds the configured export limit; narrow the filters");
            if(total>100&&!request.confirmLarge())throw new IllegalArgumentException("Confirm generation for more than 100 contracts");
            jdbc.update("UPDATE document_export_jobs SET total_count=? WHERE id=?",total,id);
        });
        activeJobs.add(id);
        try {enqueue(() -> process(id));}
        catch(RejectedExecutionException busy){activeJobs.remove(id);jdbc.update("DELETE FROM document_export_jobs WHERE id=?",id);throw new IllegalArgumentException("Export workers are busy; retry shortly");}
        return status(id,owner,0);
    }
    protected void enqueue(Runnable work){workers.execute(work);}
    public static String fingerprint(Path file) {
        try(var input=Files.newInputStream(file)) {var digest=java.security.MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);return HexFormat.of().formatHex(digest.digest());}
        catch(Exception e){throw new IllegalArgumentException("Unable to read master DOCX");}
    }
    public static String safeName(String value) {
        String clean=java.text.Normalizer.normalize(String.valueOf(value),java.text.Normalizer.Form.NFKC).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").replaceAll("^[. ]+|[. ]+$","");
        if(clean.isBlank())clean="Document";
        if(clean.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))clean="_"+clean;
        return clean.substring(0,Math.min(100,clean.length()));
    }
    private Path directory(UUID id){return root.resolve(id.toString());}
    private void process(UUID id) {
        Path directory=directory(id),part=directory.resolve("output.part");
        try {
            if(jdbc.update("UPDATE document_export_jobs SET status='RUNNING',lease_until=CURRENT_TIMESTAMP+INTERVAL '20 minutes' WHERE id=? AND status='QUEUED' AND lease_until>CURRENT_TIMESTAMP",id)!=1)return;
            var job=jdbc.queryForMap("SELECT * FROM document_export_jobs WHERE id=?",id);
            Files.createDirectories(directory);
            var master=templates.findTemplate(((Number)job.get("template_id")).longValue()).orElseThrow();
            boolean reminder=ReminderLetterService.isReminder(master);
            if(reminder) {
                var selection=DocumentJson.map(job.get("selection").toString());
                reminders.prepareExport(id,LocalDate.parse(selection.get("asOnDate").toString()),currentUser(((Number)job.get("user_id")).longValue()));
            }
            boolean zip=Boolean.TRUE.equals(job.get("all_matching"))||((Number)job.get("total_count")).longValue()>1;
            String extension=job.get("output_format").equals("PDF")?"pdf":"docx";
            Set<String> names=new HashSet<>();long last=0,success=0,failed=0,totalBytes=0;
            try(OutputStream raw=boundedOutput(Files.newOutputStream(part),maxBytes);ZipOutputStream archive=zip?new ZipOutputStream(raw):null) {
                while(true) {
                    if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Export interrupted");
                    var items=jdbc.queryForList("SELECT contract_id,document_values FROM document_export_items WHERE job_id=? AND contract_id>? ORDER BY contract_id LIMIT 100",id,last);
                    if(items.isEmpty())break;
                    for(var item:items) {
                        long contract=((Number)item.get("contract_id")).longValue();last=contract;UUID generation=null;String name=null;
                        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT status='RUNNING' AND lease_until>CURRENT_TIMESTAMP FROM document_export_jobs WHERE id=?",Boolean.class,id)))throw new IllegalStateException("Export lease lost");
                        try {
                            var auth=currentUser(((Number)job.get("user_id")).longValue());
                            if(!contracts.isEligibleForDocument(contract,ReportAccessScope.from(auth)))throw new AccessDeniedException("Contract is not eligible or accessible");
                            var template=templates.findTemplate(((Number)job.get("template_id")).longValue()).orElseThrow();
                            var file=templates.findTemplateFile(template.id()).orElseThrow();
                            if(!Boolean.TRUE.equals(template.isActive())||!template.languageCode().equals(job.get("language_code"))||!fingerprint(storage.path(file.storagePath())).equals(job.get("template_hash")))throw new IllegalArgumentException("Master document changed; start a new export");
                            var accessible=automation.accessibleContract(contract,auth);
                            String number=String.valueOf(accessible.get("contract_number"));
                            jdbc.update("UPDATE document_export_items SET contract_number=? WHERE job_id=? AND contract_id=?",number,id,contract);
                            var request=new GenerateWordDocumentRequest("CONTRACT",contract,template.id(),template.languageCode(),null);
                            var preview=reminder?automation.preview(request,template.languageCode(),auth,id,ReminderLetterService.values(item.get("document_values")))
                                :automation.preview(request,template.languageCode(),auth,id);
                            generation=UUID.fromString(preview.get("generationId").toString());
                            jdbc.update("UPDATE document_export_items SET generation_id=? WHERE job_id=? AND contract_id=?",generation,id,contract);
                            // The finalized one-click workflow accepts validated, unmodified Hindi values and snapshots their cache versions.
                            if(template.languageCode().equals("hi"))automation.approve(generation,Map.of(),false,auth);
                            var document=automation.finalizeDocument(generation,auth);
                            name=safeName(job.get("template_name").toString())+"_"+safeName(number)+"."+extension;
                            String baseName=name.substring(0,name.lastIndexOf('.'));int collision=0;
                            while(!names.add(name.toLowerCase(Locale.ROOT)))name=baseName+"_"+contract+(collision++==0?"":"_"+collision)+"."+extension;
                            Path generated=directory.resolve("current.docx"),converted=null;
                            try {
                                try(var input=document.resource().getInputStream()){Files.copy(input,generated,StandardCopyOption.REPLACE_EXISTING);}
                                Path output=generated;if(extension.equals("pdf")){converted=pdf.convert(generated);output=converted;}
                                totalBytes=Math.addExact(totalBytes,Files.size(output));
                                if(totalBytes>maxBytes)throw new IOException("Export exceeds storage limit");
                                if(zip){archive.putNextEntry(new ZipEntry(name));Files.copy(output,archive);archive.closeEntry();}else Files.copy(output,raw);
                            }finally {Files.deleteIfExists(generated);if(converted!=null)Files.deleteIfExists(converted);}
                            jdbc.update("UPDATE document_generation_audit SET output_format=?,filename=? WHERE id=?",job.get("output_format"),name,generation);
                            jdbc.update("UPDATE document_export_items SET status='SUCCESS',filename=? WHERE job_id=? AND contract_id=?",name,id,contract);success++;
                        } catch(IOException fatal) {throw new UncheckedIOException(fatal);}
                        catch(Exception error) {
                            String reason=error instanceof AccessDeniedException?"Contract is not eligible or accessible":error instanceof IllegalArgumentException?error.getMessage():"Document processing failed";
                            if(reason==null)reason="Document processing failed";reason=reason.substring(0,Math.min(450,reason.length()));
                            if(generation==null) {
                                var attempts=jdbc.queryForList("SELECT id FROM document_generation_audit WHERE batch_id=? AND contract_id=? ORDER BY started_at DESC LIMIT 1",id,contract);
                                if(!attempts.isEmpty())generation=(UUID)attempts.getFirst().get("id");
                                else {generation=UUID.randomUUID();jdbc.update("INSERT INTO document_generation_audit(id,batch_id,contract_id,template_id,template_name,language_code,user_id,username,status,failure_reason,completed_at) VALUES(?,?,?,?,?,?,?,?,'FAILED',?,CURRENT_TIMESTAMP)",generation,id,contract,job.get("template_id"),job.get("template_name"),job.get("language_code"),job.get("user_id"),job.get("username"),reason);}
                                jdbc.update("UPDATE document_export_items SET generation_id=? WHERE job_id=? AND contract_id=?",generation,id,contract);
                            }
                            if(generation!=null)jdbc.update("UPDATE document_generation_audit SET status='FAILED',preview=NULL,output_format=?,failure_reason=?,completed_at=CURRENT_TIMESTAMP WHERE id=?",job.get("output_format"),reason,generation);
                            jdbc.update("UPDATE document_export_items SET status='FAILED',error_message=?,generation_id=COALESCE(generation_id,(SELECT id FROM document_generation_audit WHERE batch_id=? AND contract_id=? ORDER BY started_at DESC LIMIT 1)) WHERE job_id=? AND contract_id=?",reason,id,contract,id,contract);failed++;
                        }
                        if(jdbc.update("UPDATE document_export_jobs SET success_count=?,failure_count=? WHERE id=? AND status='RUNNING' AND lease_until>CURRENT_TIMESTAMP",success,failed,id)!=1)throw new IllegalStateException("Export lease lost");
                        if(Files.size(part)>maxBytes)throw new IllegalStateException("Export exceeds configured storage limit");
                    }
                }
                if(zip&&failed>0) {
                    archive.putNextEntry(new ZipEntry("errors.csv"));archive.write("Contract ID,Contract Number,Error\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    long cursor=0;while(true){var errors=jdbc.queryForList("SELECT contract_id,contract_number,error_message FROM document_export_items WHERE job_id=? AND status='FAILED' AND contract_id>? ORDER BY contract_id LIMIT 100",id,cursor);if(errors.isEmpty())break;for(var row:errors){cursor=((Number)row.get("contract_id")).longValue();String line=cursor+","+csv(row.get("contract_number"))+","+csv(row.get("error_message"))+"\r\n";archive.write(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));}}archive.closeEntry();
                }
            }
            if(success==0){DocumentPdfConverter.deleteTree(directory);jdbc.update("UPDATE document_export_jobs SET status='FAILED',failure_reason='No documents succeeded',completed_at=CURRENT_TIMESTAMP WHERE id=?",id);return;}
            String filename=zip?safeName(job.get("template_name").toString())+"_"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))+".zip":jdbc.queryForObject("SELECT filename FROM document_export_items WHERE job_id=? AND status='SUCCESS'",String.class,id);
            Path complete=directory.resolve("download."+(zip?"zip":extension));Files.move(part,complete,StandardCopyOption.ATOMIC_MOVE);
            if(jdbc.update("UPDATE document_export_jobs SET status=?,artifact_path=?,artifact_name=?,artifact_size=?,completed_at=CURRENT_TIMESTAMP WHERE id=? AND status='RUNNING' AND lease_until>CURRENT_TIMESTAMP",failed==0?"COMPLETED":"COMPLETED_WITH_ERRORS",complete.toString(),filename,Files.size(complete),id)!=1)throw new IllegalStateException("Export lease lost");
        }catch(Exception error) {
            jdbc.update("UPDATE document_export_items SET status='FAILED',error_message='Export stopped before this contract completed' WHERE job_id=? AND status='PENDING'",id);
            jdbc.update("UPDATE document_export_jobs SET status='FAILED',success_count=(SELECT COUNT(*) FROM document_export_items WHERE job_id=? AND status='SUCCESS'),failure_count=(SELECT COUNT(*) FROM document_export_items WHERE job_id=? AND status='FAILED'),failure_reason='Export stopped; no partial download is available',completed_at=CURRENT_TIMESTAMP WHERE id=?",id,id,id);
            jdbc.update("UPDATE document_generation_audit SET status='FAILED',preview=NULL,failure_reason='Export interrupted',completed_at=CURRENT_TIMESTAMP WHERE batch_id=? AND status IN ('STARTED','PREVIEW','APPROVED','FINALIZING')",id);
            DocumentPdfConverter.deleteTree(directory);
        }finally {activeJobs.remove(id);}
    }
    static OutputStream boundedOutput(OutputStream output,long limit) {
        return new FilterOutputStream(output) {
            long written;
            @Override public void write(int b)throws IOException {reserve(1);out.write(b);}
            @Override public void write(byte[] b,int off,int len)throws IOException {reserve(len);out.write(b,off,len);}
            private void reserve(int count)throws IOException {if(count>limit-written)throw new IOException("Export exceeds storage limit");written+=count;}
        };
    }
    static String csv(Object value){String s=value==null?"":value.toString();if(s.matches("(?s)^[\\s\\p{Cntrl}]*[=+@-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
    private Map<String,Object> owned(UUID id,Authentication auth) {
        require(auth);currentUser(DocumentAutomationService.userId(auth));var rows=jdbc.queryForList("SELECT * FROM document_export_jobs WHERE id=? AND user_id=?",id,DocumentAutomationService.userId(auth));
        if(rows.isEmpty())throw new AccessDeniedException("Export not accessible");return rows.getFirst();
    }
    public Map<String,Object> status(UUID id,Authentication auth,int errorPage) {
        var job=owned(id,auth);Map<String,Object> result=new LinkedHashMap<>(job);result.remove("artifact_path");result.remove("selection");result.remove("template_hash");
        result.put("errors",jdbc.queryForList("SELECT contract_id,contract_number,error_message FROM document_export_items WHERE job_id=? AND status='FAILED' ORDER BY contract_id LIMIT 100 OFFSET ?",id,Math.max(0,errorPage)*100L));
        result.put("errorPage",Math.max(0,errorPage));return result;
    }
    public record Download(String name,String contentType,FileSystemResource resource) {}
    public Download download(UUID id,Authentication auth) {
        var job=owned(id,auth);var fresh=currentUser(DocumentAutomationService.userId(auth));
        if(!Set.of("COMPLETED","COMPLETED_WITH_ERRORS").contains(job.get("status")))throw new IllegalArgumentException("Export is not ready");
        if(((java.sql.Timestamp)job.get("expires_at")).toInstant().isBefore(Instant.now()))throw new IllegalArgumentException("Export expired; generate it again");
        if(contracts.exportHasInaccessibleContracts(id,ReportAccessScope.from(fresh)))throw new AccessDeniedException("Contract access changed; generate a new export");
        Path path=Path.of(job.get("artifact_path").toString()).toAbsolutePath().normalize();
        if(!path.startsWith(directory(id))||!Files.isRegularFile(path))throw new IllegalArgumentException("Export file is unavailable");
        String name=job.get("artifact_name").toString();String type=name.endsWith(".zip")?"application/zip":name.endsWith(".pdf")?"application/pdf":"application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return new Download(name,type,new FileSystemResource(path));
    }
    public void cleanup() {
        if(reminders!=null)reminders.cleanup();
        if(!workers.isShutdown())for(UUID id:activeJobs)jdbc.update("UPDATE document_export_jobs SET lease_until=CURRENT_TIMESTAMP+INTERVAL '20 minutes' WHERE id=? AND status IN ('QUEUED','RUNNING') AND lease_until>CURRENT_TIMESTAMP",id);
        // Fence expired workers atomically before touching their items or files. Never replay AI calls after a crash.
        var stale=transaction.execute(tx -> {
          var expiredJobs=jdbc.queryForList("UPDATE document_export_jobs SET status='FAILED',failure_reason='Worker interrupted; start a new export',completed_at=CURRENT_TIMESTAMP WHERE status IN ('QUEUED','RUNNING') AND lease_until<=CURRENT_TIMESTAMP RETURNING id");
          for(var expired:expiredJobs) {
            UUID id=(UUID)expired.get("id");
            jdbc.update("UPDATE document_export_items SET status='FAILED',error_message='Worker interrupted; start a new export' WHERE job_id=? AND status='PENDING'",id);
            jdbc.update("UPDATE document_export_jobs SET success_count=(SELECT COUNT(*) FROM document_export_items WHERE job_id=? AND status='SUCCESS'),failure_count=(SELECT COUNT(*) FROM document_export_items WHERE job_id=? AND status='FAILED') WHERE id=?",id,id,id);
            jdbc.update("UPDATE document_generation_audit SET status='FAILED',preview=NULL,failure_reason='Worker interrupted',completed_at=CURRENT_TIMESTAMP WHERE batch_id=? AND status IN ('STARTED','PREVIEW','APPROVED','FINALIZING')",id);
          }
          return expiredJobs;
        });
        for(var expired:Objects.requireNonNull(stale))DocumentPdfConverter.deleteTree(directory((UUID)expired.get("id")));
        jdbc.update("UPDATE document_generation_audit SET preview=NULL WHERE expires_at<CURRENT_TIMESTAMP AND preview IS NOT NULL AND status IN ('PREVIEW','APPROVED','FAILED')");
        for(var job:jdbc.queryForList("SELECT id FROM document_export_jobs WHERE expires_at<CURRENT_TIMESTAMP AND status NOT IN ('QUEUED','RUNNING') AND status<>'EXPIRED'")) {
            if(DocumentPdfConverter.deleteTree(directory((UUID)job.get("id"))))jdbc.update("UPDATE document_export_jobs SET status='EXPIRED',artifact_path=NULL WHERE id=?",job.get("id"));
        }
    }
}
