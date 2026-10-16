package dugar_lms_api.modules.reports.stockstatement;

import java.util.Arrays;
import java.util.ArrayList;

/** Same comma/blank-component/PIN rules as DemandListGrid.formatPartyAddress. */
final class StockStatementAddress {
    private StockStatementAddress() {}

    static String format(String name, String address) {
        var parts = new ArrayList<>(Arrays.stream((address == null ? "" : address).split(","))
            .map(StockStatementAddress::clean).filter(part -> !part.isEmpty()).toList());
        String formatted = "";
        if (!parts.isEmpty()) {
            String pin = parts.getLast();
            if (pin.matches("\\d{5,6}") && parts.size() > 1) {
                parts.removeLast();
                formatted = String.join(", ", parts) + " - " + pin;
            } else {
                formatted = String.join(", ", parts);
            }
        }
        String borrower = clean(name);
        return borrower.isEmpty() ? formatted : formatted.isEmpty() ? borrower : borrower + "\n" + formatted;
    }

    private static String clean(String value) {
        String cleaned = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return cleaned.equalsIgnoreCase("null") ? "" : cleaned;
    }
}
