package com.learnagent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Small repository proving the database boundary; business repositories arrive in later phases. */
@Repository
public class HealthProbeRepository {
  private final JdbcTemplate jdbcTemplate;
  public HealthProbeRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
  public boolean canConnect() {
    try { return Integer.valueOf(1).equals(jdbcTemplate.queryForObject("select 1", Integer.class)); }
    catch (Exception ignored) { return false; }
  }
}
