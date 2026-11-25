package dugar_lms_api.modules.reports.repaymentschedule;

import dugar_lms_api.modules.accessmanagement.service.MenuAccessService;
import dugar_lms_api.modules.contracts.dto.LoanReferenceOptionDto;
import dugar_lms_api.modules.contracts.service.ContractListService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
public class RepaymentSchedulePagesController {
    static final String WITH_INTEREST = "OPERATIONS_TRANSACTIONS_REPAYMENT_SCHEDULE_WITH_INTEREST";
    static final String NORMAL = "OPERATIONS_TRANSACTIONS_REPAYMENT_SCHEDULE_NORMAL";

    private final MenuAccessService menus;
    private final ContractListService contracts;
    private final NormalRepaymentScheduleService normal;
    private final NormalRepaymentSchedulePdf pdf;

    public RepaymentSchedulePagesController(MenuAccessService menus, ContractListService contracts,
                                            NormalRepaymentScheduleService normal, NormalRepaymentSchedulePdf pdf) {
        this.menus = menus;
        this.contracts = contracts;
        this.normal = normal;
        this.pdf = pdf;
    }

    @GetMapping("/repayment-schedules/{variant}/contracts")
    public ResponseEntity<List<LoanReferenceOptionDto>> search(@PathVariable String variant, @RequestParam String keyword,
                                                               Authentication authentication) {
        String menu = switch (variant) {
            case "with-interest" -> WITH_INTEREST;
            case "normal" -> NORMAL;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        requireMenu(menu, authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(contracts.getLoanReferenceOptions(keyword, 15, authentication));
    }

    @GetMapping("/{contractId}/repayment-schedule/normal/pdf")
    public ResponseEntity<byte[]> generateNormal(@PathVariable long contractId, Authentication authentication) {
        requireMenu(NORMAL, authentication);
        var report = normal.load(contractId, authentication);
        try {
            return RepaymentScheduleController.pdfResponse("Repayment_Schedule_Normal_", report.details().contractNumber(),
                pdf.generate(report));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to generate the repayment schedule PDF.", exception);
        }
    }

    private void requireMenu(String menu, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in to access repayment schedules.");
        }
        if (!menus.canView(menu, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Repayment schedule permission is required.");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        return RepaymentScheduleController.errorResponse(exception);
    }
}
