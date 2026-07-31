package dugar_lms_api.migration.assetinsurance;

record AssetCandidate(
    Long assetId,
    String registrationNumber,
    String engineNumber,
    String chassisNumber,
    String sourceRowHash
) {
}
