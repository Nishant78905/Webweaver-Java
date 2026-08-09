package com.akatsuki.webweaver.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class CrawlDatabaseService {

    private final JdbcTemplate jdbcTemplate;

    public CrawlDatabaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // This runs automatically when Spring Boot starts up
    @PostConstruct
    public void initDatabase() {
        // Create table for pages
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS crawled_pages (" +
                "url TEXT PRIMARY KEY, " +
                "topic TEXT, " +
                "relevance_score REAL" +
                ");");

        // Create table for edges (links between pages)
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS page_edges (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "source_url TEXT, " +
                "target_url TEXT" +
                ");");

        System.out.println("SQLite Database Initialized Successfully!");
    }

    // Save a page to the database
    public void savePage(String url, String topic, double score) {
        String sql = "INSERT OR IGNORE INTO crawled_pages (url, topic, relevance_score) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, url, topic, score);
    }

    // Save an edge to the database
    public void saveEdge(String source, String target) {
        String sql = "INSERT OR IGNORE INTO page_edges (source_url, target_url) VALUES (?, ?)";
        jdbcTemplate.update(sql, source, target);
    }
}