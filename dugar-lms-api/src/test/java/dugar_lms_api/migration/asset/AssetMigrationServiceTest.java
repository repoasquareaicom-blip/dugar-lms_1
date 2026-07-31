package dugar_lms_api.migration.asset;

import dugar_lms_api.migration.common.MigrationRunService;
import dugar_lms_api.modules.assets.dto.AssetCreateDto;
import dugar_lms_api.modules.assets.dto.AssetDto;
import dugar_lms_api.modules.assets.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetMigrationServiceTest {

    private AssetRepository repository;
    private AssetMigrationService service;

    @BeforeEach
    void setUp() {
        repository = mock(AssetRepository.class);
        service = new AssetMigrationService(
            mock(AssetMigrationExcelReader.class),
            repository,
            mock(MigrationRunService.class),
            transactionManager()
        );
    }

    @Test
    void trimAndUppercaseContractMatchingIsUsed() {
        AssetMigrationRow row = row(" hp ", " 001 ", "ENG-1", "CH-1");
        when(repository.findContractId("HP", "001")).thenReturn(Optional.of(10L));
        when(repository.findByBusinessKey(eq(10L), eq("CHASSIS"), eq("CH-1"))).thenReturn(List.of());
        when(repository.insert(any())).thenReturn(1L);

        assertThat(service.importRow(row, supplements(), counts("HP/001"))).isEqualTo(AssetMigrationService.RowOutcome.INSERTED);

        verify(repository).findContractId("HP", "001");
    }

    @Test
    void missingContractIsNotInserted() {
        AssetMigrationRow row = row("HP", "404", "ENG-1", "CH-1");
        when(repository.findContractId("HP", "404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importRow(row, supplements(), counts("HP/404")))
            .isInstanceOf(AssetMigrationService.MissingContractException.class)
            .hasMessage("Missing contract for HP/404");
        verify(repository, never()).insert(any());
    }

    @Test
    void duplicateRerunSkipsIdenticalExistingRecord() {
        AssetMigrationRow row = row("HP", "001", "eng-1", "ch-1");
        ContractAssetSupplement supplement = new ContractAssetSupplement("HP", "001", "TN 01 AB 1234", "2018");
        when(repository.findContractId("HP", "001")).thenReturn(Optional.of(10L));
        when(repository.findByBusinessKey(eq(10L), eq("CHASSIS"), eq("CH-1"))).thenReturn(List.of(asset(
            10L,
            "HP",
            "001",
            "HP",
            "O",
            "TN01AB1234",
            "eng-1",
            "ch-1",
            "2018",
            BigDecimal.ZERO,
            "BANK",
            "1"
        )));

        assertThat(service.importRow(row, Map.of("HP/001", supplement), counts("HP/001")))
            .isEqualTo(AssetMigrationService.RowOutcome.DUPLICATE);
        verify(repository, never()).insert(any());
    }

    @Test
    void conflictDetectionSkipsDifferingExistingRecord() {
        AssetMigrationRow row = row("HP", "001", "ENG-1", "CH-1");
        when(repository.findContractId("HP", "001")).thenReturn(Optional.of(10L));
        when(repository.findByBusinessKey(eq(10L), eq("CHASSIS"), eq("CH-1"))).thenReturn(List.of(asset(
            10L,
            "HP",
            "001",
            "HP",
            "T",
            null,
            "ENG-1",
            "CH-1",
            null,
            BigDecimal.ZERO,
            "DIFFERENT",
            "1"
        )));

        assertThatThrownBy(() -> service.importRow(row, supplements(), counts("HP/001")))
            .isInstanceOf(AssetMigrationService.AssetConflictException.class)
            .hasMessageContaining("vehicle_type_code")
            .hasMessageContaining("security_offered");
        verify(repository, never()).insert(any());
    }

    @Test
    void blankChassisFallsBackToEngine() {
        AssetMigrationRecord record = service.toRecord(row("HP", "001", " eng-9 ", "."), 10L, null);

        AssetMigrationService.BusinessKey businessKey = service.businessKey(record);

        assertThat(businessKey.type()).isEqualTo("ENGINE");
        assertThat(businessKey.value()).isEqualTo("ENG-9");
    }

    @Test
    void blankChassisAndEngineFallBackToRegistration() {
        AssetMigrationRow row = row("HP", "001", null, null);
        ContractAssetSupplement supplement = new ContractAssetSupplement("HP", "001", " TN-01 AB 1234 ", "2019");
        AssetMigrationRecord record = service.toRecord(row, 10L, supplement);

        AssetMigrationService.BusinessKey businessKey = service.businessKey(record);

        assertThat(businessKey.type()).isEqualTo("REGISTRATION");
        assertThat(businessKey.value()).isEqualTo("TN01AB1234");
    }

    @Test
    void placeholderValuesAreTreatedAsNull() {
        AssetMigrationRow row = row("HP", "001", "NULL", "..");
        ContractAssetSupplement supplement = new ContractAssetSupplement("HP", "001", ".", "N/A");
        AssetMigrationRecord record = service.toRecord(row, 10L, supplement);

        assertThat(record.engineNumber()).isNull();
        assertThat(record.chassisNumber()).isNull();
        assertThat(record.registrationNumber()).isNull();
        assertThat(record.manufactureYear()).isNull();
        assertThat(service.businessKey(record).type()).isEqualTo("SOURCE_ROW_HASH");
    }

    @Test
    void duplicateSourceContractRowsAreConflicts() {
        AssetMigrationRow row = row(" HP ", " 14625 ", "ENG-1", "CH-1");

        assertThatThrownBy(() -> service.importRow(row, supplements(), Map.of("HP/14625", 2)))
            .isInstanceOf(AssetMigrationService.DuplicateSourceContractException.class)
            .hasMessage("Duplicate HP_CONTR_DETAILS rows for HP/14625");
        verify(repository, never()).findContractId(any(), any());
        verify(repository, never()).insert(any());
    }

    @Test
    void validationIssuesAreWarningsNotProcessFailure() {
        AssetMigrationSummary summary = AssetMigrationSummary.base();
        summary.incrementMissingContracts();
        summary.incrementConflicts();
        summary.incrementDuplicates();
        summary.completeProcessStatus();

        assertThat(summary.isSuccess()).isTrue();
        assertThat(summary.isCompletedWithWarnings()).isTrue();
    }

    @Test
    void duplicateOnlyRerunIsSuccessfulWithWarnings() {
        AssetMigrationSummary summary = AssetMigrationSummary.base();
        summary.setSuccess(false);
        summary.incrementDuplicates();
        summary.completeProcessStatus();

        assertThat(summary.getInserted()).isZero();
        assertThat(summary.getDuplicates()).isPositive();
        assertThat(summary.getFailed()).isZero();
        assertThat(summary.isSuccess()).isTrue();
        assertThat(summary.isCompletedWithWarnings()).isTrue();
    }

    @Test
    void insertedAssetPreservesTrimmedSourceIdentifiers() {
        AssetMigrationRow row = row("HP", "001", " eng-1 ", " ch-1 ");
        when(repository.findContractId("HP", "001")).thenReturn(Optional.of(10L));
        when(repository.findByBusinessKey(eq(10L), eq("CHASSIS"), eq("CH-1"))).thenReturn(List.of());
        when(repository.insert(any())).thenReturn(1L);

        service.importRow(row, supplements(), counts("HP/001"));

        org.mockito.ArgumentCaptor<AssetCreateDto> captor = org.mockito.ArgumentCaptor.forClass(AssetCreateDto.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().engineNumber()).isEqualTo("eng-1");
        assertThat(captor.getValue().chassisNumber()).isEqualTo("ch-1");
    }

    private AssetMigrationRow row(String contractType, String contractNumber, String engineNumber, String chassisNumber) {
        return new AssetMigrationRow(
            2,
            contractType,
            contractNumber,
            "O",
            BigDecimal.ZERO,
            "BANK",
            engineNumber,
            chassisNumber,
            "1"
        );
    }

    private Map<String, ContractAssetSupplement> supplements() {
        return Map.of("HP/001", new ContractAssetSupplement("HP", "001", null, null));
    }

    private Map<String, Integer> counts(String key) {
        return Map.of(key, 1);
    }

    private AssetDto asset(
        Long contractId,
        String oracleContractType,
        String oracleContractNo,
        String financeType,
        String vehicleTypeCode,
        String registrationNumber,
        String engineNumber,
        String chassisNumber,
        String manufactureYear,
        BigDecimal equipmentValue,
        String securityOffered,
        String ownerSerialNo
    ) {
        return new AssetDto(
            1L,
            contractId,
            oracleContractType,
            oracleContractNo,
            financeType,
            vehicleTypeCode,
            registrationNumber,
            engineNumber,
            chassisNumber,
            manufactureYear,
            equipmentValue,
            securityOffered,
            ownerSerialNo,
            AssetMigrationService.PRIMARY_SOURCE_TABLE,
            "hash",
            null,
            null
        );
    }

    private PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
