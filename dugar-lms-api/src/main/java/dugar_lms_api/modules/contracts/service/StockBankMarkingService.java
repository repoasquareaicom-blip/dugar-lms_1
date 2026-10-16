package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.StockBankMarkingDto;
import dugar_lms_api.modules.contracts.repository.StockBankMarkingRepository;
import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockBankMarkingService {
    private final StockBankMarkingRepository repository;
    private final ContractAccessRepository access;
    public StockBankMarkingService(StockBankMarkingRepository repository, ContractAccessRepository access) {
        this.repository = repository; this.access = access;
    }
    public StockBankMarkingDto search(String number, Authentication authentication) {
        if (number == null || number.isBlank()) throw new IllegalArgumentException("Contract Number is required.");
        var matches = repository.byNumber(number.trim());
        if (matches.size() != 1) throw new IllegalArgumentException("Contract not found or contract number is ambiguous.");
        var contract = matches.getFirst();
        access.requireAccess(contract.contractId(), ReportAccessScope.from(authentication));
        return contract;
    }
    // Shared by Contract Add/Edit and the standalone transaction. Lock the same parent row.
    public void validateBank(Long contractId, Long bankId) {
        repository.lockContract(contractId);
        repository.validateBank(bankId, repository.byId(contractId).bankId());
    }
    @Transactional
    public StockBankMarkingDto save(Long contractId, Long bankId, Authentication authentication) {
        access.requireAccess(contractId, ReportAccessScope.from(authentication));
        if (bankId == null) throw new IllegalArgumentException("Bank selection is required.");
        repository.lockActiveContract(contractId);
        validateBank(contractId, bankId);
        var scope = ReportAccessScope.from(authentication);
        String user = scope.userId() != null ? scope.userId().toString() : authentication.getName();
        repository.save(contractId, bankId, user);
        return repository.byId(contractId);
    }
}
