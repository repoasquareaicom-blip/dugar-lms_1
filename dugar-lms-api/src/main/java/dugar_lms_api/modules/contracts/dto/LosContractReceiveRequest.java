package dugar_lms_api.modules.contracts.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record LosContractReceiveRequest(
    Long id,
    @JsonAlias("created_by")
    Long createdBy,
    @JsonAlias("ar_user_id")
    Long arUserId,
    @JsonAlias("ar_username")
    String arUsername,
    @JsonAlias("agent_request_number")
    String agentRequestNumber,
    @JsonAlias("borrower_name")
    String borrowerName,
    @JsonAlias("date_of_birth")
    LocalDate dateOfBirth,
    @JsonAlias("distance_from_office")
    BigDecimal distanceFromOffice,
    String initials,
    @JsonAlias("mobile_number")
    String mobileNumber,
    String occupation,
    @JsonAlias("house_status")
    String houseStatus,
    String email,
    String city,
    @JsonAlias("aadhar_number")
    String aadharNumber,
    @JsonAlias("pan_number")
    String panNumber,
    @JsonAlias("vehicle_name")
    String vehicleName,
    @JsonAlias("rc_number")
    String rcNumber,
    String model,
    @JsonAlias("loan_amount")
    BigDecimal loanAmount,
    @JsonAlias("co_applicant_name")
    String coApplicantName,
    @JsonAlias("co_applicant_mobile")
    String coApplicantMobile,
    @JsonAlias("co_applicant_relationship")
    String coApplicantRelationship,
    @JsonAlias("created_at")
    LocalDateTime createdAt,
    Integer status,
    @JsonAlias("disbursed_date")
    LocalDate disbursedDate,
    @JsonAlias("hp_number")
    String hpNumber,
    @JsonAlias("reference_number")
    String referenceNumber,
    @JsonAlias("product_type")
    String productType,
    String km,
    @JsonAlias("kilometers_driven")
    BigDecimal kilometersDriven,
    @JsonAlias("approved_amount")
    BigDecimal approvedAmount,
    @JsonAlias({"approved_category", "category", "proposal_category"})
    String approvedCategory,
    @JsonAlias("approver_id")
    Long approverId,
    @JsonAlias("sanction_amount")
    BigDecimal sanctionAmount,
    @JsonAlias("first_emi")
    BigDecimal firstEmi,
    @JsonAlias("rc_holding")
    BigDecimal rcHolding,
    @JsonAlias("processing_charge")
    BigDecimal processingCharge,
    @JsonAlias("rto_expenses")
    BigDecimal rtoExpenses,
    @JsonAlias("other_expenses")
    BigDecimal otherExpenses,
    @JsonAlias("net_disbursal")
    BigDecimal netDisbursal,
    @JsonAlias("primary_name")
    String primaryName,
    @JsonAlias("primary_ac_number")
    String primaryAcNumber,
    @JsonAlias("primary_ac_type")
    String primaryAcType,
    @JsonAlias("primary_bank")
    String primaryBank,
    @JsonAlias("primary_ifsc")
    String primaryIfsc,
    @JsonAlias("secondary_name")
    String secondaryName,
    @JsonAlias("secondary_ac_number")
    String secondaryAcNumber,
    @JsonAlias("secondary_ac_type")
    String secondaryAcType,
    @JsonAlias("secondary_bank")
    String secondaryBank,
    @JsonAlias("secondary_ifsc")
    String secondaryIfsc,
    @JsonAlias("primary_disbursal_amount")
    BigDecimal primaryDisbursalAmount,
    @JsonAlias("secondary_disbursal_amount")
    BigDecimal secondaryDisbursalAmount,
    @JsonAlias({"Vehicle_Id", "vehicle_id"})
    Long vehicleId,
    BigDecimal irr,
    @JsonAlias("insurance_expiry_date")
    LocalDate insuranceExpiryDate,
    @JsonAlias("IDV")
    BigDecimal idv,
    @JsonAlias("vehicle_type")
    String vehicleType,
    @JsonAlias("fuel_type")
    String fuelType,
    @JsonAlias("interest_rate")
    BigDecimal interestRate,
    @JsonAlias({"risk_level", "risk", "risc", "risc_level"})
    String riskLevel,
    @JsonAlias("emi_number1")
    Integer emiNumber1,
    @JsonAlias("emi_amount1")
    BigDecimal emiAmount1,
    @JsonAlias("emi_number2")
    Integer emiNumber2,
    @JsonAlias("emi_amount2")
    BigDecimal emiAmount2,
    @JsonAlias("emi_number3")
    Integer emiNumber3,
    @JsonAlias("emi_amount3")
    BigDecimal emiAmount3,
    @JsonAlias({"tvr_done_by", "tvrDoneBy", "tvr_done"})
    String tvrDoneBy,
    @JsonAlias("documents_verified_by")
    String documentsVerifiedBy,
    @JsonAlias("disbursed_by")
    Long disbursedBy,
    @JsonAlias({"documents", "proposal_documents"})
    List<LosProposalDocumentDto> documents
) {
}
