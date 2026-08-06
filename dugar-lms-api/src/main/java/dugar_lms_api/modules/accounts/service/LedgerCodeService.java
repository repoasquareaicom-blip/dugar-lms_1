package dugar_lms_api.modules.accounts.service;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.accounts.dto.LedgerActiveRequest;
import dugar_lms_api.modules.accounts.dto.LedgerCodeDto;
import dugar_lms_api.modules.accounts.dto.LedgerCodeRequest;
import dugar_lms_api.modules.accounts.repository.LedgerCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerCodeService {

    private final LedgerCodeRepository repository;

    public LedgerCodeService(LedgerCodeRepository repository) {
        this.repository = repository;
    }

    public PageResponse<LedgerCodeDto> find(LedgerCodeCriteria criteria) {
        return repository.find(criteria);
    }

    @Transactional
    public LedgerCodeDto create(LedgerCodeRequest request, Long userId) {
        return repository.create(request, userId);
    }

    @Transactional
    public LedgerCodeDto update(String ledgerCode, LedgerCodeRequest request, Long userId) {
        return repository.update(ledgerCode, request, userId);
    }

    public LedgerCodeDto updateActive(String ledgerCode, LedgerActiveRequest request, Long userId) {
        return repository.updateActive(ledgerCode, Boolean.TRUE.equals(request.isActive()), userId);
    }
}
