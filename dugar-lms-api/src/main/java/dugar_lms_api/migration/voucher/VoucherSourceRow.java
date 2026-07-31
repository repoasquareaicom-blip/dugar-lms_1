package dugar_lms_api.migration.voucher;

import java.util.Locale;
import java.util.Map;

public record VoucherSourceRow(
    int excelRow,
    Map<String, Object> values
) {
    public Object value(String headerName) {
        if (headerName == null) {
            return null;
        }
        return values.get(headerName.toUpperCase(Locale.ROOT));
    }

    public boolean isBlank() {
        return values.values().stream().allMatch(value -> value == null || value.toString().isBlank());
    }
}
