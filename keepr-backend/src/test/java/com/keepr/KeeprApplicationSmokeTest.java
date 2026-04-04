package com.keepr;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.keepr.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test that validates the full application context boots with real
 * Postgres and Redis containers, Flyway migrations apply cleanly,
 * and the /health endpoint responds correctly.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KeeprApplicationSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    /**
     * Validates the /health endpoint returns HTTP 200 with the expected body.
     */
    @Test
    void healthEndpointReturnsUpWithSprint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.sprint").value(1));
    }

    /**
     * Validates all 9 expected tables exist after Flyway migrations.
     */
    @Test
    void allTablesExistAfterMigration() throws Exception {
        List<String> expectedTables = List.of(
                "users",
                "households",
                "household_members",
                "devices",
                "invoices",
                "device_invoices",
                "warranties",
                "extraction_jobs",
                "notifications",
                "household_invites",
                "user_notification_preferences",
                "auth_otp"
        );

        List<String> actualTables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(null, "public", "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    actualTables.add(rs.getString("TABLE_NAME"));
                }
            }
        }

        for (String table : expectedTables) {
            assertThat(actualTables)
                    .as("Expected table '%s' to exist", table)
                    .contains(table);
        }
    }

    /**
     * Validates the application context loads successfully.
     * If this test passes, the Spring context (including Flyway) started without errors.
     */
    @Test
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }
}
