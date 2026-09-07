package dugar_lms_api.modules.reports;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportAccessScopeTest {

    @Test
    void adminGroupIsUnrestricted() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("admin1", null);
        authentication.setDetails(Map.of("userGroup", "admin"));

        ReportAccessScope scope = ReportAccessScope.from(authentication);

        assertThat(scope.restrictedToUserGroup()).isFalse();
        assertThat(scope.userGroup()).isEqualTo("admin");
    }

    @Test
    void userGroupIsRestricted() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("staff1", null);
        authentication.setDetails(Map.of("userGroup", "user"));

        ReportAccessScope scope = ReportAccessScope.from(authentication);

        assertThat(scope.restrictedToUserGroup()).isTrue();
        assertThat(scope.userGroup()).isEqualTo("user");
    }

    @Test
    void missingGroupIsRestricted() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("unknown", null);

        ReportAccessScope scope = ReportAccessScope.from(authentication);
        ReportAccessScope nullScope = ReportAccessScope.from(null);

        assertThat(scope.restrictedToUserGroup()).isTrue();
        assertThat(scope.userGroup()).isNull();
        assertThat(nullScope.restrictedToUserGroup()).isTrue();
        assertThat(nullScope.userGroup()).isNull();
    }

    @Test
    void unknownGroupIsRestricted() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("unknown", null);
        authentication.setDetails(Map.of("userGroup", "unexpected"));

        ReportAccessScope scope = ReportAccessScope.from(authentication);

        assertThat(scope.restrictedToUserGroup()).isTrue();
        assertThat(scope.userGroup()).isEqualTo("unexpected");
    }
}
