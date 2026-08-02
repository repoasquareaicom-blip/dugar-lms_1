package dugar_lms_api.modules.contracts.service;

import dugar_lms_api.modules.contracts.dto.ContractPartyDraftRequest;
import dugar_lms_api.modules.contracts.dto.ContractPartyDraftResponse;
import dugar_lms_api.modules.contracts.dto.PartyDraftDto;
import dugar_lms_api.modules.contracts.dto.PartyDraftSaveResult;
import dugar_lms_api.modules.contracts.repository.ContractPartyDraftRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ContractPartyDraftService {

    private final ContractPartyDraftRepository contractPartyDraftRepository;

    public ContractPartyDraftService(ContractPartyDraftRepository contractPartyDraftRepository) {
        this.contractPartyDraftRepository = contractPartyDraftRepository;
    }

    @Transactional
    public ContractPartyDraftResponse saveParties(ContractPartyDraftRequest request, Authentication authentication) {
        String updatedBy = auditUser(authentication);
        List<PartyDraftSaveResult> results = new ArrayList<>();
        if (request == null || request.parties() == null) {
            return new ContractPartyDraftResponse(null, null, false, results);
        }

        for (PartyDraftDto party : request.parties()) {
            if (party == null || isBlank(party.role()) || isEmptyParty(party)) {
                continue;
            }

            PartyRole role = PartyRole.from(party.role());
            String partyCode = clean(party.partyCode());
            boolean created = false;

            if (partyCode == null || !contractPartyDraftRepository.exists(partyCode)) {
                Long partyId = contractPartyDraftRepository.nextPartyId();
                partyCode = role.prefix() + String.format("%06d", partyId);
                contractPartyDraftRepository.insertParty(partyId, partyCode, role.partyType(), party, updatedBy);
                created = true;
            } else {
                contractPartyDraftRepository.updateParty(partyCode, party, updatedBy);
            }

            results.add(new PartyDraftSaveResult(role.requestRole(), partyCode, role.partyType(), created));
        }

        ContractDraftLink link = linkFrom(results);
        Long contractId = request.contractId();
        boolean contractCreated = false;
        String contractNumber;

        if (contractId == null) {
            contractId = contractPartyDraftRepository.nextContractId();
            Long auditId = contractPartyDraftRepository.nextContractAuditId();
            contractNumber = "ND" + String.format("%06d", contractId);
            contractPartyDraftRepository.insertContractDraft(
                contractId,
                auditId,
                contractNumber,
                link.borrowerCode(),
                link.coApplicantCode(),
                link.guarantorCode(),
                link.guarantor2Code(),
                updatedBy
            );
            contractCreated = true;
        } else {
            contractPartyDraftRepository.updateContractDraft(
                contractId,
                link.borrowerCode(),
                link.coApplicantCode(),
                link.guarantorCode(),
                link.guarantor2Code(),
                updatedBy
            );
            contractNumber = contractPartyDraftRepository.findContractNumber(contractId);
        }

        return new ContractPartyDraftResponse(contractId, contractNumber, contractCreated, results);
    }

    public ContractPartyDraftResponse getParties(Long contractId) {
        String contractNumber = contractPartyDraftRepository.findContractNumber(contractId);
        List<PartyDraftSaveResult> parties = contractPartyDraftRepository.findContractParties(contractId)
            .stream()
            .map((party) -> new PartyDraftSaveResult(party.role(), party.partyCode(), partyTypeForRole(party.role()), false))
            .toList();

        return new ContractPartyDraftResponse(contractId, contractNumber, false, parties);
    }

    public List<PartyDraftDto> getPartyDetails(Long contractId) {
        return contractPartyDraftRepository.findContractParties(contractId);
    }

    private String partyTypeForRole(String role) {
        return PartyRole.from(role).partyType();
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

    private ContractDraftLink linkFrom(List<PartyDraftSaveResult> results) {
        String borrowerCode = null;
        String coApplicantCode = null;
        String guarantorCode = null;
        String guarantor2Code = null;

        for (PartyDraftSaveResult result : results) {
            if ("applicant".equals(result.role())) borrowerCode = result.partyCode();
            if ("coApplicant".equals(result.role())) coApplicantCode = result.partyCode();
            if ("guarantor1".equals(result.role())) guarantorCode = result.partyCode();
            if ("guarantor2".equals(result.role())) guarantor2Code = result.partyCode();
        }

        return new ContractDraftLink(borrowerCode, coApplicantCode, guarantorCode, guarantor2Code);
    }

    private boolean isEmptyParty(PartyDraftDto party) {
        return isBlank(party.fullName())
            && isBlank(party.firmName())
            && isBlank(party.panNumber())
            && isBlank(party.aadhaarNumber())
            && isBlank(party.contactNumber());
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private enum PartyRole {
        APPLICANT("applicant", "BORROWER", "NH"),
        CO_APPLICANT("coApplicant", "CO_APPLICANT", "NHC"),
        GUARANTOR_1("guarantor1", "GUARANTOR", "NG"),
        GUARANTOR_2("guarantor2", "GUARANTOR", "NG");

        private final String requestRole;
        private final String partyType;
        private final String prefix;

        PartyRole(String requestRole, String partyType, String prefix) {
            this.requestRole = requestRole;
            this.partyType = partyType;
            this.prefix = prefix;
        }

        static PartyRole from(String value) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "applicant", "borrower", "primary" -> APPLICANT;
                case "coapplicant", "co-applicant", "co_applicant", "coapp" -> CO_APPLICANT;
                case "guarantor1", "guarantor_1", "g1" -> GUARANTOR_1;
                case "guarantor2", "guarantor_2", "g2" -> GUARANTOR_2;
                default -> throw new IllegalArgumentException("Unsupported party role: " + value);
            };
        }

        String requestRole() {
            return requestRole;
        }

        String partyType() {
            return partyType;
        }

        String prefix() {
            return prefix;
        }
    }

    private record ContractDraftLink(
        String borrowerCode,
        String coApplicantCode,
        String guarantorCode,
        String guarantor2Code
    ) {
    }
}
