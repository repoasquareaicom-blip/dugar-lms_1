package dugar_lms_api.modules.masters.service;

import dugar_lms_api.common.pagination.PageResponse;
import dugar_lms_api.modules.masters.dto.BankMasterDto;
import dugar_lms_api.modules.masters.dto.BankMasterRequest;
import dugar_lms_api.modules.masters.repository.BankMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BankMasterService {

    private final BankMasterRepository repository;

    public BankMasterService(BankMasterRepository repository) {
        this.repository = repository;
    }

    public PageResponse<BankMasterDto> find(BankMasterCriteria criteria) {
        return repository.find(criteria);
    }

    public List<BankMasterDto> findActive() {
        return repository.findActive();
    }

    @Transactional
    public BankMasterDto create(BankMasterRequest request) {
        return repository.create(request);
    }

    @Transactional
    public BankMasterDto update(Long id, BankMasterRequest request) {
        return repository.update(id, request);
    }
}
