package com.automate.CodeReview.sonar;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository @RequiredArgsConstructor
public class SonarIssueDao {
    private final JdbcTemplate sonarJdbc;

    /** ลิสต์ issues VULNERABILITY (เอาเฉพาะยังไม่ปิด) */
    public List<Map<String,Object>> findIssues(String projectKey) {
        String sql = """
      WITH prj AS (SELECT uuid FROM projects WHERE kee = ?)
      SELECT i.kee            AS issue_key,
             i.rule_uuid      AS rule_uuid,
             i.severity       AS legacy_severity,   -- รุ่นเก่า
             i.type,
             i.message,
             i.component_uuid,
             i.line,
             i.creation_date,
             i.update_date,
             i.tags           AS issue_tags         -- ถ้ามี
      FROM issues i
      WHERE i.project_uuid = (SELECT uuid FROM prj)
        AND i.type = 'VULNERABILITY'
        AND i.resolution IS NULL
    """;
        return sonarJdbc.queryForList(sql, projectKey);
    }

    /** นับ OWASP จาก issue.tags ถ้ามี */
    public Map<String,Integer> countOwaspFromIssueTags(String projectKey){
        String sql = """
      WITH prj AS (SELECT uuid FROM projects WHERE kee = ?),
      src AS (
        SELECT COALESCE(i.tags,'') AS tags
        FROM issues i
        WHERE i.project_uuid = (SELECT uuid FROM prj)
          AND i.type = 'VULNERABILITY'
          AND i.resolution IS NULL
      )
      SELECT CASE WHEN tags ~* 'owasp-a(\\d{2})'
                  THEN 'A' || regexp_replace(tags, '.*owasp-a(\\d{2}).*', '\\1')
                  ELSE 'UNMAPPED' END AS owasp_id,
             COUNT(*) AS cnt
      FROM src GROUP BY 1
    """;
        Map<String,Integer> out = new LinkedHashMap<>();
        for (var r: sonarJdbc.queryForList(sql, projectKey))
            out.put((String)r.get("owasp_id"), ((Number)r.get("cnt")).intValue());
        return out;
    }

    /** นับ OWASP จาก rule tags (กรณี issues.tags ว่าง) */
    public Map<String,Integer> countOwaspFromRuleTags(String projectKey){
        String sql = """
      WITH prj AS (SELECT uuid FROM projects WHERE kee = ?),
      it AS (
        SELECT DISTINCT i.rule_uuid
        FROM issues i
        WHERE i.project_uuid = (SELECT uuid FROM prj)
          AND i.type = 'VULNERABILITY'
          AND i.resolution IS NULL
      ),
      rt AS (
        SELECT t.name AS tag_name
        FROM rules r
        JOIN rule_tags rt ON rt.rule_uuid = r.uuid   -- บางรุ่นชื่อ rules_tags
        JOIN tags t      ON t.uuid = rt.tag_uuid
        WHERE r.uuid IN (SELECT rule_uuid FROM it)
      )
      SELECT 'A' || regexp_replace(tag_name, '.*owasp-a(\\d{2}).*', '\\1') AS owasp_id,
             COUNT(*) AS cnt
      FROM rt
      WHERE tag_name ~* 'owasp-a(\\d{2})'
      GROUP BY 1 ORDER BY 1
    """;
        Map<String,Integer> out = new LinkedHashMap<>();
        for (var r: sonarJdbc.queryForList(sql, projectKey))
            out.put((String)r.get("owasp_id"), ((Number)r.get("cnt")).intValue());
        return out;
    }

    /** นับความรุนแรงแบบใหม่ (ถ้ามีตาราง issues_impacts) */
    public Map<String,Integer> countImpactSeverities(String projectKey){
        String sql = """
      WITH prj AS (SELECT uuid FROM projects WHERE kee = ?)
      SELECT ii.severity, COUNT(*) AS cnt
      FROM issues i
      JOIN issues_impacts ii ON ii.issue_key = i.kee
      WHERE i.project_uuid = (SELECT uuid FROM prj)
        AND i.type = 'VULNERABILITY'
        AND i.resolution IS NULL
      GROUP BY ii.severity
    """;
        Map<String,Integer> out = new LinkedHashMap<>();
        try {
            for (var r: sonarJdbc.queryForList(sql, projectKey))
                out.put(((String)r.get("severity")).toUpperCase(), ((Number)r.get("cnt")).intValue());
        } catch (Exception ignore) { /* ตารางไม่มีในรุ่นเก่า */ }
        return out;
    }

    /** นับ legacy severities (รุ่นเก่า: BLOCKER/CRITICAL/MAJOR/...) */
    public Map<String,Integer> countLegacySeverities(String projectKey){
        String sql = """
      WITH prj AS (SELECT uuid FROM projects WHERE kee = ?)
      SELECT i.severity, COUNT(*) AS cnt
      FROM issues i
      WHERE i.project_uuid = (SELECT uuid FROM prj)
        AND i.type = 'VULNERABILITY'
        AND i.resolution IS NULL
      GROUP BY i.severity
    """;
        Map<String,Integer> out = new LinkedHashMap<>();
        for (var r: sonarJdbc.queryForList(sql, projectKey))
            out.put(((String)r.get("severity")).toUpperCase(), ((Number)r.get("cnt")).intValue());
        return out;
    }

    /** map component_uuid -> path (ไฟล์) */
    public Map<String,String> componentPathsByUuid(Collection<String> uuids){
        if (uuids.isEmpty()) return Collections.emptyMap();
        String in = String.join(",", uuids.stream().map(s -> "'" + s + "'").toList());
        String sql = "SELECT uuid, path FROM components WHERE uuid IN (" + in + ")";
        Map<String,String> out = new HashMap<>();
        for (var r : sonarJdbc.queryForList(sql))
            out.put((String)r.get("uuid"), (String)r.get("path"));
        return out;
    }

    /** rule uuid -> rule key */
    public Map<String,String> ruleKeysByUuid(Collection<String> uuids){
        if (uuids.isEmpty()) return Collections.emptyMap();
        String in = String.join(",", uuids.stream().map(s -> "'" + s + "'").toList());
        String sql = "SELECT uuid, rule_key FROM rules WHERE uuid IN (" + in + ")";
        Map<String,String> out = new HashMap<>();
        for (var r : sonarJdbc.queryForList(sql))
            out.put((String)r.get("uuid"), (String)r.get("rule_key"));
        return out;
    }
}

