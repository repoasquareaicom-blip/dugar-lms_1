package dugar_lms_api.modules.reports.stockstatement;

import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

@Service
public class StockStatementService {
    private final StockStatementRepository repository;
    public StockStatementService(StockStatementRepository repository) { this.repository = repository; }

    public LocalDate businessDate() { return LocalDate.now(ZoneId.of("Asia/Kolkata")); }

    @Transactional
    public StockStatementResponse generate(Long bankId, ReportAccessScope scope) {
        return generate(bankId, businessDate(), scope);
    }

    @Transactional
    public StockStatementResponse generate(Long bankId, LocalDate date, ReportAccessScope scope) {
        if (date == null) throw new IllegalArgumentException("As On Date is required.");
        if (bankId == null || bankId <= 0) throw new IllegalArgumentException("Bank selection is required.");
        var bank = repository.activeBank(bankId);
        var rows = repository.rows(bankId, date, scope);
        BigDecimal loan = BigDecimal.ZERO.setScale(2), installment = loan, outstanding = loan;
        for (var row : rows) {
            loan = loan.add(row.loanAmount());
            installment = installment.add(row.principalPerInstallment());
            outstanding = outstanding.add(row.principalOutstanding());
        }
        return new StockStatementResponse(bank.id(), bank.code(), bank.name(), date, rows, rows.size(),
            loan, installment, outstanding, fingerprint(bank, date, rows));
    }

    private String fingerprint(StockStatementRepository.Bank bank, LocalDate date, List<StockStatementRow> rows) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, bank.toString());
            update(digest, date.toString());
            for (var row : rows) update(digest, row.toString());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
