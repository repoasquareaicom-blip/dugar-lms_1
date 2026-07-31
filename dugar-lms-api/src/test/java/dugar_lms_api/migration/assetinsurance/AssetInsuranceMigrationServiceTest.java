package dugar_lms_api.migration.assetinsurance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dugar_lms_api.migration.common.MigrationRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetInsuranceMigrationServiceTest {

    private AssetInsuranceMigrationRepository repository;
    private AssetInsuranceMigrationService service;

    @BeforeEach
    void setUp() {
        repository = mock(AssetInsuranceMigrationRepository.class);
        service = new AssetInsuranceMigrationService(
            mock(AssetInsuranceExcelReader.class),
            repository,
            mock(MigrationRunService.class),
            transactionManager()
        );
    }

    @Test
    void successfulSingleInsuranceInsertion() {
        AssetInsuranceSourceRow row = row("HP", "100", "I001", "P001", "CN1", "2024-01-01", "2024-01-02", "2025-01-01", "P", "1250.50");
        givenContractAndSingleAsset();
        when(repository.findByPolicyNumber(eq(7L), eq("P001"))).thenReturn(List.of());
        when(repository.insert(any())).thenReturn(1L);

        assertThat(service.importRow(row, new AssetInsuranceMigrationService.SourceRowTracker()))
            .isEqualTo(AssetInsuranceMigrationService.RowOutcome.INSERTED);

        verify(repository).insert(any(AssetInsuranceRecord.class));
    }

    @Test
    void missingContractIsSkippedByException() {
        AssetInsuranceSourceRow row = row(" hp ", " missing ", "I001", "P001", null, null, null, null, null, null);
        when(repository.findContractId("HP", "MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importRow(row, new AssetInsuranceMigrationService.SourceRowTracker()))
            .isInstanceOf(AssetInsuranceMigrationService.MissingContractException.class)
            .hasMessage("Missing contract for HP/MISSING");
        verify(repository, never()).insert(any());
    }

    @Test
    void missingAssetIsSkippedByException() {
        AssetInsuranceSourceRow row = row("HP", "100", "I001", "P001", null, null, null, null, null, null);
        when(repository.findContractId("HP", "100")).thenReturn(Optional.of(11L));
        when(repository.findAssetsByContractId(11L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.importRow(row, new AssetInsuranceMigrationService.SourceRowTracker()))
            .isInstanceOf(AssetInsuranceMigrationService.MissingAssetException.class)
            .hasMessage("Missing asset for HP/100");
        verify(repository, never()).insert(any());
    }

    @Test
    void multipleDifferentAssetsProduceAmbiguousAsset() {
        AssetInsuranceSourceRow row = row("HP", "100", "I001", "P001", null, null, null, null, null, null);
        when(repository.findContractId("HP", "100")).thenReturn(Optional.of(11L));
        when(repository.findAssetsByContractId(11L)).thenReturn(List.of(
            new AssetCandidate(7L, "TN01AB1234", "ENG1", "CH1", "HASH1"),
            new AssetCandidate(8L, "TN01AB1234", "ENG2", "CH2", "HASH2")
        ));

        assertThatThrownBy(() -> service.importRow(row, new AssetInsuranceMigrationService.SourceRowTracker()))
            .isInstanceOf(AssetInsuranceMigrationService.AmbiguousAssetException.class)
            .hasMessage("Multiple distinct assets for HP/100");
    }

    @Test
    void duplicateRerunIsSuccessfulWithWarnings() {
        AssetInsuranceMigrationSummary summary = AssetInsuranceMigrationSummary.base();
        summary.incrementDuplicates();
        summary.completeProcessStatus();

        assertThat(summary.getInserted()).isZero();
        assertThat(summary.getDuplicates()).isPositive();
        assertThat(summary.getFailed()).isZero();
        assertThat(summary.isSuccess()).isTrue();
        assertThat(summary.isCompletedWithWarnings()).isTrue();
    }

    @Test
    void samePolicyNumberWithDifferentValuesProducesConflict() {
        AssetInsuranceSourceRow row = row("HP", "100", "I001", "P001", null, null, null, null, null, "1000");
        givenContractAndSingleAsset();
        when(repository.findByPolicyNumber(eq(7L), eq("P001"))).thenReturn(List.of(existing("I001", "P001", null, null, null, null, null, new BigDecimal("2000"))));

        assertThatThrownBy(() -> service.importRow(row, new AssetInsuranceMigrationService.SourceRowTracker()))
            .isInstanceOf(AssetInsuranceMigrationService.AssetInsuranceConflictException.class)
            .hasMessageContaining("premium_amount");
        verify(repository, never()).insert(any());
    }

    @Test
    void blankPolicyNumberUsesFallbackDuplicateDetection() {
        AssetInsuranceSourceRow row = row("HP", "100", "I001", null, "CN1", "2024-01-01", "2024-01-02", "2025-01-01", "P", "1000");
        givenContractAndSingleAsset();
        when(repository.findByFallbackKey(any())).thenReturn(List.of(existing("I001", null, "CN1", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), LocalDate.of(2025, 1, 1), "P", new BigDecimal("1000"))));

        assertThat(service.importRow(row, new AssetInsuranceMigrationService.SourceRowTracker()))
            .isEqualTo(AssetInsuranceMigrationService.RowOutcome.DUPLICATE);

        verify(repository, never()).findByPolicyNumber(any(), any());
        verify(repository).findByFallbackKey(any());
    }

    @Test
    void placeholderNormalizationTreatsValuesAsNull() {
        AssetInsuranceRecord record = service.toRecord(row("HP", "100", ".", "..", "NULL", "N/A", "NIL", "NONE", "-", "-"), 7L);

        assertThat(record.insuranceCompanyCode()).isNull();
        assertThat(record.policyNumber()).isNull();
        assertThat(record.coverNoteNumber()).isNull();
        assertThat(record.policyDate()).isNull();
        assertThat(record.validFrom()).isNull();
        assertThat(record.validTo()).isNull();
        assertThat(record.policyBy()).isNull();
        assertThat(record.premiumAmount()).isNull();
    }

    @Test
    void invalidDateHandlingRaisesRowException() {
        AssetInsuranceSourceRow row = row("HP", "100", "I001", "P001", null, "not-a-date", null, null, null, null);
        givenContractAndSingleAsset();

        assertThatThrownBy(() -> service.importRow(row, new AssetInsuranceMigrationService.SourceRowTracker()))
            .isInstanceOf(AssetInsuranceMigrationService.InvalidDateException.class)
            .hasMessage("Invalid date for POLICY_DT: not-a-date");
    }

    @Test
    void invalidAmountHandlingRaisesRowException() {
        AssetInsuranceSourceRow row = row("HP", "100", "I001", "P001", null, null, null, null, null, "ten");
        givenContractAndSingleAsset();

        assertThatThrownBy(() -> service.importRow(row, new AssetInsuranceMigrationService.SourceRowTracker()))
            .isInstanceOf(AssetInsuranceMigrationService.InvalidAmountException.class)
            .hasMessage("Invalid amount for POLICY_AMT: ten");
    }

    @Test
    void repeatedSourceRowDetection() {
        AssetInsuranceSourceRow row = row("HP", "100", "I001", "P001", null, null, null, null, null, "1000");
        AssetInsuranceMigrationService.SourceRowTracker tracker = new AssetInsuranceMigrationService.SourceRowTracker();
        givenContractAndSingleAsset();
        when(repository.findByPolicyNumber(eq(7L), eq("P001"))).thenReturn(List.of());
        when(repository.insert(any())).thenReturn(1L);

        assertThat(service.importRow(row, tracker)).isEqualTo(AssetInsuranceMigrationService.RowOutcome.INSERTED);
        assertThat(service.importRow(row, tracker)).isEqualTo(AssetInsuranceMigrationService.RowOutcome.DUPLICATE_SOURCE_ROW);
    }

    @Test
    void fatalExceptionSummaryIsUnsuccessful() {
        AssetInsuranceMigrationController controller = new AssetInsuranceMigrationController(mock(AssetInsuranceMigrationService.class));

        ResponseEntity<AssetInsuranceMigrationSummary> response = controller.handleUnexpected(new RuntimeException("database unavailable"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getIssues().getFirst().reason()).isEqualTo("database unavailable");
    }

    @Test
    void controllerReturnsExactSummaryUpdatedByService() {
        AssetInsuranceMigrationSummary summary = AssetInsuranceMigrationSummary.base();
        summary.incrementDuplicates();
        summary.completeProcessStatus();

        AssetInsuranceMigrationService mockedService = mock(AssetInsuranceMigrationService.class);
        when(mockedService.importAssetInsurances()).thenReturn(summary);
        AssetInsuranceMigrationController controller = new AssetInsuranceMigrationController(mockedService);

        ResponseEntity<AssetInsuranceMigrationSummary> response = controller.importAssetInsurances();

        assertThat(response.getBody()).isSameAs(summary);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().isCompletedWithWarnings()).isTrue();
    }

    @Test
    void jsonSerializationReturnsActualSuccessField() throws Exception {
        AssetInsuranceMigrationSummary summary = AssetInsuranceMigrationSummary.base();
        summary.incrementDuplicates();
        summary.completeProcessStatus();

        String json = new ObjectMapper().writeValueAsString(summary);

        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"completedWithWarnings\":true");
    }

    private void givenContractAndSingleAsset() {
        when(repository.findContractId("HP", "100")).thenReturn(Optional.of(11L));
        when(repository.findAssetsByContractId(11L)).thenReturn(List.of(new AssetCandidate(7L, "TN01AB1234", "ENG1", "CH1", "HASH1")));
    }

    private AssetInsuranceSourceRow row(
        String contractType,
        String contractNumber,
        String insuranceCompanyCode,
        String policyNumber,
        String coverNoteNumber,
        String policyDate,
        String validFrom,
        String validTo,
        String policyBy,
        String premiumAmount
    ) {
        return new AssetInsuranceSourceRow(
            2,
            contractType,
            contractNumber,
            insuranceCompanyCode,
            policyNumber,
            coverNoteNumber,
            policyDate,
            validFrom,
            validTo,
            policyBy,
            premiumAmount
        );
    }

    private AssetInsuranceExistingRecord existing(
        String insuranceCompanyCode,
        String policyNumber,
        String coverNoteNumber,
        LocalDate policyDate,
        LocalDate validFrom,
        LocalDate validTo,
        String policyBy,
        BigDecimal premiumAmount
    ) {
        AssetInsuranceRecord incoming = new AssetInsuranceRecord(
            7L,
            insuranceCompanyCode,
            policyNumber,
            policyNumber == null ? null : policyNumber.toUpperCase(java.util.Locale.ROOT),
            coverNoteNumber,
            policyDate,
            validFrom,
            validTo,
            policyBy,
            premiumAmount,
            "hash"
        );
        return new AssetInsuranceExistingRecord(
            1L,
            incoming.assetId(),
            incoming.insuranceCompanyCode(),
            incoming.policyNumber(),
            incoming.coverNoteNumber(),
            incoming.policyDate(),
            incoming.validFrom(),
            incoming.validTo(),
            incoming.policyBy(),
            incoming.premiumAmount(),
            incoming.sourceRowHash()
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
