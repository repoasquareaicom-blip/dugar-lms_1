package dugar_lms_api.migration.asset;

public record ContractAssetSupplement(
    String contractType,
    String contractNumber,
    String registrationNumber,
    String manufactureYear
) {
}
