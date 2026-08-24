package dugar_lms_api.modules.reports.aginganalysis;

import java.time.LocalDate;
import java.util.List;

public record ConsolidatedPortfolioResponse(
    LocalDate asOnDate,
    String areaCode,
    List<ConsolidatedPortfolioRowDto> tenorWise,
    List<ConsolidatedPortfolioRowDto> ticketSizeWise,
    List<ConsolidatedPortfolioStateRowDto> stateWise
) {
}
