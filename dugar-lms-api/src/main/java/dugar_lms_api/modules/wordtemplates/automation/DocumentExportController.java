package dugar_lms_api.modules.wordtemplates.automation;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.*;
import java.util.*;

@RestController
@RequestMapping("/api/document-exports")
public class DocumentExportController {
    @org.springframework.beans.factory.annotation.Autowired
    private ReminderLetterService reminders;
    private final DocumentExportService exports;
    public DocumentExportController(DocumentExportService exports){this.exports=exports;}
    @PostMapping("/reminder/snapshots") public Object reminderSnapshot(@RequestBody ReminderSnapshotRequest request,Authentication auth) {
        return reminders.snapshot(request.asOnDate(),auth);
    }
    public record ReminderSnapshotRequest(java.time.LocalDate asOnDate) {}
    @GetMapping("/reminder/snapshots/{id}") public Object reminderRows(@PathVariable UUID id,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOnDate,
            @RequestParam(defaultValue="true") boolean pendingOnly,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,Authentication auth) {
        return reminders.rows(id,asOnDate,pendingOnly,search,page,size,auth);
    }
    @PostMapping public ResponseEntity<?> submit(@RequestBody DocumentExportRequest request,Authentication auth){return ResponseEntity.accepted().body(exports.submit(request,auth));}
    @GetMapping("/{id}") public Object status(@PathVariable UUID id,@RequestParam(defaultValue="0") int errorPage,Authentication auth){return exports.status(id,auth,errorPage);}
    @GetMapping("/{id}/download") public ResponseEntity<?> download(@PathVariable UUID id,Authentication auth) {
        var file=exports.download(id,auth);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.name(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"no-store").body(file.resource());
    }
    @ExceptionHandler(AccessDeniedException.class) public ResponseEntity<?> denied(AccessDeniedException e){return ResponseEntity.status(403).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
}
