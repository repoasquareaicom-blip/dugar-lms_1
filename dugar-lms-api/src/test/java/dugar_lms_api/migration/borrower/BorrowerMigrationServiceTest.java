package dugar_lms_api.migration.borrower;

import dugar_lms_api.migration.common.MigrationRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

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

class BorrowerMigrationServiceTest {

    private BorrowerMigrationRepository repository;
    private MigrationRunService migrationRunService;
    private BorrowerMigrationService service;

    @BeforeEach
    void setUp() {
        repository = mock(BorrowerMigrationRepository.class);
        migrationRunService = mock(MigrationRunService.class);
        service = new BorrowerMigrationService(
            mock(BorrowerExcelReader.class),
            repository,
            migrationRunService,
            transactionManager()
        );
    }

    @Test
    void hMapsToBorrower() {
        PartyMasterRecord party = service.toParty(row(" H00123 ", "H", "RAHUL"));

        assertThat(party.partyType()).isEqualTo(PartyType.BORROWER);
        assertThat(party.partyCode()).isEqualTo("H00123");
    }

    @Test
    void gMapsToGuarantor() {
        PartyMasterRecord party = service.toParty(row("G010641", "g", "UMESH"));

        assertThat(party.partyType()).isEqualTo(PartyType.GUARANTOR);
    }

    @Test
    void leadingZerosInPartyCodeArePreserved() {
        PartyMasterRecord party = service.toParty(row("001234", "H", "MEENA"));

        assertThat(party.partyCode()).isEqualTo("001234");
    }

    @Test
    void partyName1MapsToFullNameAndPartyName2MapsToSwdNameWithoutConcatenation() {
        BorrowerMigrationRow row = new BorrowerMigrationRow(
            2,
            "H001",
            "MR",
            "Ramesh Kumar",
            "S/o Gopal",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "H"
        );

        PartyMasterRecord party = service.toParty(row);

        assertThat(party.fullName()).isEqualTo("Ramesh Kumar");
        assertThat(party.swdName()).isEqualTo("S/o Gopal");
    }

    @Test
    void blankRowsAreSkipped() {
        BorrowerMigrationRow row = new BorrowerMigrationRow(5, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(service.importRow(row)).isEqualTo(BorrowerMigrationService.RowOutcome.SKIPPED_BLANK);
        verify(repository, never()).insertParty(any(), any());
    }

    @Test
    void repeatedHeadersAreSkipped() {
        assertThat(service.importRow(row("PARTY_CODE", "PARTY_TYPE", "PARTY_NAME1")))
            .isEqualTo(BorrowerMigrationService.RowOutcome.SKIPPED_HEADER);
    }

    @Test
    void missingPartyCodeFailsOnlyThatRow() {
        assertThatThrownBy(() -> service.importRow(row(null, "H", "RAHUL")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PARTY_CODE is required");
    }

    @Test
    void missingFullNameFailsOnlyThatRow() {
        assertThatThrownBy(() -> service.importRow(row("H001", "H", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PARTY_NAME1 is required");
    }

    @Test
    void invalidClassificationIsLoggedAsInvalid() {
        assertThatThrownBy(() -> service.importRow(row("H001", "X", "RAHUL")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid party type: X");
    }

    @Test
    void identicalExistingRecordIsDuplicate() {
        BorrowerMigrationRow row = row("H001", "H", "RAHUL");
        when(repository.findByPartyCode("H001")).thenReturn(Optional.of(service.toParty(row)));

        assertThat(service.importRow(row)).isEqualTo(BorrowerMigrationService.RowOutcome.DUPLICATE);
    }

    @Test
    void differentExistingRecordIsConflict() {
        BorrowerMigrationRow row = row("H001", "H", "RAHUL");
        when(repository.findByPartyCode("H001")).thenReturn(Optional.of(service.toParty(row("H001", "H", "RAVI"))));

        assertThatThrownBy(() -> service.importRow(row))
            .isInstanceOf(BorrowerMigrationService.PartyConflictException.class)
            .hasMessage("Existing party has different mapped values");
    }

    @Test
    void optionalBlanksRemainNull() {
        PartyMasterRecord party = service.toParty(row("H001", "H", "RAHUL"));

        assertThat(party.salutation()).isNull();
        assertThat(party.addressLine1()).isNull();
        assertThat(party.alternativeNumber()).isNull();
        assertThat(party.dateOfBirth()).isNull();
    }

    @Test
    void oneFailedRowDoesNotAffectSuccessfulRows() {
        when(repository.findByPartyCode("H002")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importRow(row(null, "H", "BROKEN")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.importRow(row("H002", "H", "VALID"))).isEqualTo(BorrowerMigrationService.RowOutcome.INSERTED);

        verify(repository).insertParty(any(PartyMasterRecord.class), eq(BorrowerMigrationService.CREATED_BY));
    }

    @Test
    void missingContractHirerCodesAreDetected() {
        when(repository.distinctContractBorrowerCodes()).thenReturn(Set.of("H001", "H002"));
        when(repository.distinctContractGuarantorCodes()).thenReturn(Set.of());
        when(repository.existingPartyCodes(Set.of("H001", "H002"), PartyType.BORROWER)).thenReturn(Set.of("H001"));
        when(repository.existingPartyCodes(Set.of(), PartyType.GUARANTOR)).thenReturn(Set.of());

        BorrowerMigrationSummary summary = new BorrowerMigrationSummary();
        service.validateContractReferences(10L, summary);

        assertThat(summary.getDistinctContractHirerCodes()).isEqualTo(2);
        assertThat(summary.getMatchedHirerCodes()).isEqualTo(1);
        assertThat(summary.getMissingHirerCodes()).isEqualTo(1);
        verify(migrationRunService).recordMissingReference(eq(10L), eq("H002"), eq("Missing party master for contract borrower_code"), any());
    }

    @Test
    void missingContractGuarantorCodesAreDetected() {
        when(repository.distinctContractBorrowerCodes()).thenReturn(Set.of());
        when(repository.distinctContractGuarantorCodes()).thenReturn(Set.of("G001", "G002"));
        when(repository.existingPartyCodes(Set.of(), PartyType.BORROWER)).thenReturn(Set.of());
        when(repository.existingPartyCodes(Set.of("G001", "G002"), PartyType.GUARANTOR)).thenReturn(Set.of("G001"));

        BorrowerMigrationSummary summary = new BorrowerMigrationSummary();
        service.validateContractReferences(11L, summary);

        assertThat(summary.getDistinctContractGuarantorCodes()).isEqualTo(2);
        assertThat(summary.getMatchedGuarantorCodes()).isEqualTo(1);
        assertThat(summary.getMissingGuarantorCodes()).isEqualTo(1);
        verify(migrationRunService).recordMissingReference(eq(11L), eq("G002"), eq("Missing party master for contract guarantor_code"), any());
    }

    @Test
    void contractReferenceStatsNormalizeCodesAndUseExpectedPartyType() {
        when(repository.distinctContractBorrowerCodes()).thenReturn(Set.of(" h012388 ", "h009999"));
        when(repository.distinctContractGuarantorCodes()).thenReturn(Set.of(" g010641 "));
        when(repository.existingPartyCodes(Set.of("H012388", "H009999"), PartyType.BORROWER)).thenReturn(Set.of(" h012388 "));
        when(repository.existingPartyCodes(Set.of("G010641"), PartyType.GUARANTOR)).thenReturn(Set.of("g010641"));

        BorrowerMigrationSummary summary = new BorrowerMigrationSummary();
        service.validateContractReferences(12L, summary);

        assertThat(summary.getDistinctContractHirerCodes()).isEqualTo(2);
        assertThat(summary.getMatchedHirerCodes()).isEqualTo(1);
        assertThat(summary.getMissingHirerCodes()).isEqualTo(1);
        assertThat(summary.getDistinctContractGuarantorCodes()).isEqualTo(1);
        assertThat(summary.getMatchedGuarantorCodes()).isEqualTo(1);
        assertThat(summary.getMissingGuarantorCodes()).isZero();
        verify(migrationRunService).recordMissingReference(eq(12L), eq("H009999"), eq("Missing party master for contract borrower_code"), any());
    }

    @Test
    void migrationSummaryCountsCanRemainConsistent() {
        BorrowerMigrationSummary summary = new BorrowerMigrationSummary();
        summary.incrementEligibleRows();
        summary.incrementBorrowerRows();
        summary.incrementInserted();
        summary.incrementEligibleRows();
        summary.incrementGuarantorRows();
        summary.incrementDuplicates();
        summary.incrementFailed();
        summary.incrementSkippedBlankRows();

        assertThat(summary.getBorrowerRows() + summary.getGuarantorRows()).isEqualTo(summary.getEligibleRows());
        assertThat(summary.getInserted() + summary.getDuplicates() + summary.getFailed()).isEqualTo(summary.getEligibleRows() + summary.getFailed());
    }

    private BorrowerMigrationRow row(String partyCode, String partyType, String fullName) {
        return new BorrowerMigrationRow(
            2,
            partyCode,
            null,
            fullName,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            partyType
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
