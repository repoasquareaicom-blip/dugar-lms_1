package dugar_lms_api.modules.wordtemplates.automation;

import dugar_lms_api.modules.contracts.service.ContractListCriteria;
import java.util.List;
public record DocumentExportRequest(Long templateId,String outputFormat,boolean allMatching,List<Long> contractIds,
        ContractListCriteria criteria,boolean confirmLarge,java.time.LocalDate asOnDate,java.util.UUID reminderSnapshotId,
        boolean pendingOnly,String search) {
    public DocumentExportRequest(Long templateId,String outputFormat,boolean allMatching,List<Long> contractIds,ContractListCriteria criteria,boolean confirmLarge) {
        this(templateId,outputFormat,allMatching,contractIds,criteria,confirmLarge,null,null,false,null);
    }
}
