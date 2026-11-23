package dugar_lms_api.modules.reports.repaymentschedule;

import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.SQLException;

@Service
public class RepaymentScheduleService {
    private final ContractAccessRepository access;
    private final RepaymentScheduleRepository repository;
    public RepaymentScheduleService(ContractAccessRepository access, RepaymentScheduleRepository repository) {
        this.access = access; this.repository = repository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RepaymentScheduleData load(long contractId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in to generate a repayment schedule.");
        }
        if (contractId <= 0 || !access.canAccess(contractId, ReportAccessScope.from(authentication))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found or not accessible.");
        }
        var header = repository.header(contractId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found or not accessible."));
        try {
            var rows = repository.schedule(header.contractNumber());
            if (rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No contractual repayment schedule is available.");
            }
            return new RepaymentScheduleData(header, repository.slabs(contractId), rows, repository.totals(header.contractNumber()));
        } catch (DataAccessException exception) {
            if (exception.getMostSpecificCause() instanceof SQLException sql && "22023".equals(sql.getSQLState())) {
                String reason = sql.getMessage() == null ? "Unsupported contractual repayment schedule."
                    : sql.getMessage().split("[\\r\\n]", 2)[0].replaceFirst("^ERROR: *", "");
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason, exception);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Repayment schedule is temporarily unavailable.", exception);
        }
    }
}
