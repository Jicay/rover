package com.jicay.rover.component

import io.cucumber.java.Before
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class DatabaseCleanupHooks {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Before
    fun emptyDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE obstacles, rovers, boards RESTART IDENTITY CASCADE")
    }
}
