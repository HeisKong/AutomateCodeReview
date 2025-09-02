// SonarQueryService.java
package com.automate.CodeReview.Service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class SonarQueryService {

    private final JdbcTemplate sonarJdbc;

    public SonarQueryService(@Qualifier("sonarJdbcTemplate") JdbcTemplate sonarJdbc) {
        this.sonarJdbc = sonarJdbc;
    }

    /** ดึงสถานะ Quality Gate ของโปรเจกต์ */
    public Optional<String> getQualityGateStatus(String projectKey) {
        String sql = """
            SELECT COALESCE(pm.alert_status, pm.text_value) AS status
            FROM projects p
            JOIN project_branches pb ON pb.project_uuid = p.uuid AND pb.is_main = TRUE
            JOIN project_measures pm ON pm.component_uuid = pb.uuid
            JOIN metrics m ON m.uuid = pm.metric_uuid AND m.name = 'alert_status'
            WHERE p.kee = ?
            LIMIT 1
        """;
        try {
            return Optional.ofNullable(sonarJdbc.queryForObject(sql, String.class, projectKey));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
