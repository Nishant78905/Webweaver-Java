package com.akatsuki.webweaver.service;

import com.akatsuki.webweaver.model.WebGraph;
import com.akatsuki.webweaver.model.ParsedPage; // ✅ Fixed Import
import com.akatsuki.webweaver.util.HtmlParserUtil;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CrawlerService {

    private final HtmlParserUtil htmlParserUtil;
    private final LinkHeuristicScorer heuristicScorer;
    private final CrawlDatabaseService dbService;

    public CrawlerService(HtmlParserUtil htmlParserUtil, LinkHeuristicScorer heuristicScorer, CrawlDatabaseService dbService) {
        this.htmlParserUtil = htmlParserUtil;
        this.heuristicScorer = heuristicScorer;
        this.dbService = dbService;
    }

    public WebGraph crawl(String startUrl, String topic, int topK, int maxDepth, int maxPages) {
        WebGraph graph = new WebGraph();

        Set<String> visited = ConcurrentHashMap.newKeySet();
        Queue<String> currentLevel = new ConcurrentLinkedQueue<>();

        currentLevel.add(startUrl);
        visited.add(startUrl);

        dbService.savePage(startUrl, topic, 1.0);

        AtomicInteger pagesCrawled = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        try {
            for (int depth = 0; depth <= maxDepth; depth++) {
                if (currentLevel.isEmpty() || pagesCrawled.get() >= maxPages) break;

                System.out.println("🕸️ Crawling Depth " + depth + " | URLs processing: " + currentLevel.size());

                Queue<String> nextLevel = new ConcurrentLinkedQueue<>();
                List<Future<?>> futures = new ArrayList<>();

                while (!currentLevel.isEmpty() && pagesCrawled.get() < maxPages) {
                    String url = currentLevel.poll();

                    futures.add(executor.submit(() -> {
                        // ✅ MATCHED 1: parsePage(url)
                        ParsedPage parsedPage = htmlParserUtil.parsePage(url);
                        if (parsedPage == null) return;

                        int currentCount = pagesCrawled.incrementAndGet();
                        if (currentCount > maxPages) return;

                        Map<String, Double> scoredLinks = new HashMap<>();

                        // ✅ MATCHED 2: Extract the Map of links and loop through it
                        Map<String, String> linksMap = parsedPage.getLinks();
                        if (linksMap != null) {
                            for (Map.Entry<String, String> entry : linksMap.entrySet()) {
                                String targetLink = entry.getKey();
                                String anchorText = entry.getValue();

                                // ✅ MATCHED 3: score(url, anchor, topic)
                                double score = heuristicScorer.score(targetLink, anchorText, topic);
                                scoredLinks.put(targetLink, score);
                            }
                        }

                        // Sort and slice Top-K
                        List<Map.Entry<String, Double>> sortedLinks = new ArrayList<>(scoredLinks.entrySet());
                        sortedLinks.sort((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

                        int count = 0;
                        for (Map.Entry<String, Double> entry : sortedLinks) {
                            if (count >= topK) break;
                            String targetUrl = entry.getKey();
                            double score = entry.getValue();

                            synchronized (graph) {
                                graph.addEdge(url, targetUrl);
                                graph.setRelevanceScore(targetUrl, score);
                            }

                            try {
                                dbService.saveEdge(url, targetUrl);
                                dbService.savePage(targetUrl, topic, score);
                            } catch (Exception ignored) {}

                            if (visited.add(targetUrl)) {
                                nextLevel.add(targetUrl);
                            }
                            count++;
                        }
                    }));
                }

                // Wait for all threads to finish this depth level
                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (InterruptedException | ExecutionException ignored) {}
                }

                currentLevel = nextLevel;
            }
        } finally {
            executor.shutdown();
        }

        return graph;
    }
}