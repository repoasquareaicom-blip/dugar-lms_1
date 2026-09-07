package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.ContractAssetDraftDto;
import dugar_lms_api.modules.contracts.repository.ContractAccessRepository;
import dugar_lms_api.modules.contracts.repository.ContractAssetDraftRepository;
import dugar_lms_api.modules.reports.ReportAccessScope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ContractAssetDraftService {

    private final ContractAssetDraftRepository contractAssetDraftRepository;
    private final ContractAccessRepository contractAccessRepository;

    public ContractAssetDraftService(
        ContractAssetDraftRepository contractAssetDraftRepository,
        ContractAccessRepository contractAccessRepository
    ) {
        this.contractAssetDraftRepository = contractAssetDraftRepository;
        this.contractAccessRepository = contractAccessRepository;
    }

    public ContractAssetDraftDto getAssetDetails(Long contractId, Authentication authentication) {
        contractAccessRepository.requireAccess(contractId, ReportAccessScope.from(authentication));
        return contractAssetDraftRepository.findByContractId(contractId).orElse(null);
    }

    @Transactional
    public ContractAssetDraftDto saveAssetDetails(Long contractId, ContractAssetDraftDto request, Authentication authentication) {
        contractAccessRepository.requireAccess(contractId, ReportAccessScope.from(authentication));
        ContractAssetDraftDto existing = contractAssetDraftRepository.findByContractId(contractId).orElse(null);
        Long assetId = existing == null ? null : existing.assetId();
        Long assetInsuranceId = existing == null ? null : existing.assetInsuranceId();

        ContractAssetDraftDto asset = withIds(contractId, assetId, assetInsuranceId, request);
        Long savedAssetId = contractAssetDraftRepository.upsertAsset(asset);
        ContractAssetDraftDto savedAsset = withIds(contractId, savedAssetId, assetInsuranceId, request);

        contractAssetDraftRepository.updateContractSummary(savedAsset, auditUser(authentication));
        contractAssetDraftRepository.upsertContractDetail(savedAsset, auditUser(authentication));

        Long savedInsuranceId = contractAssetDraftRepository.upsertInsurance(
            savedAssetId,
            assetInsuranceId,
            savedAsset
        );

        return contractAssetDraftRepository.findByContractId(contractId)
            .orElse(withIds(contractId, savedAssetId, savedInsuranceId, request));
    }

    private ContractAssetDraftDto withIds(
        Long contractId,
        Long assetId,
        Long assetInsuranceId,
        ContractAssetDraftDto asset
    ) {
        return new ContractAssetDraftDto(
            contractId,
            assetId,
            assetInsuranceId,
            asset.assetSecured(),
            asset.productType(),
            asset.vehicleTypeCode(),
            asset.dealOfAssets(),
            asset.vehicleMake(),
            asset.version(),
            asset.manufactureYear(),
            asset.ownerSerialNo(),
            asset.registrationNumber(),
            asset.fuelType(),
            asset.kmsRun(),
            asset.marketValue(),
            asset.chassisNumber(),
            asset.engineNumber(),
            asset.idv(),
            asset.insuranceExpiry(),
            asset.insuranceCompany(),
            asset.insurancePremium(),
            asset.propertyType(),
            asset.flatNo(),
            asset.apartmentNo(),
            asset.streetName(),
            asset.areaName(),
            asset.propertyCity(),
            asset.propertyState(),
            asset.distanceFromOffice(),
            asset.anyRentReceived(),
            asset.rentAmount(),
            asset.guidelineValue(),
            asset.natureOfBusiness(),
            asset.dateOfIncorporation(),
            asset.isSecured(),
            asset.proposalCategory(),
            asset.riskLevel()
        );
    }

    private String auditUser(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId != null) {
                return String.valueOf(userId);
            }
        }
        return authentication != null && authentication.getName() != null ? authentication.getName() : "system";
    }
}
