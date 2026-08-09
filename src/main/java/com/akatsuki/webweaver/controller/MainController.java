package com.akatsuki.webweaver.controller;

import com.akatsuki.webweaver.model.WebGraph;
import com.akatsuki.webweaver.service.CrawlerService;
import com.akatsuki.webweaver.service.PageRankService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.control.Hyperlink;
import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MainController {

    private final CrawlerService crawlerService;
    private final PageRankService pageRankService;

    // FXML UI Elements
    @FXML private TextField urlInput;
    @FXML private TextField topicInput;
    @FXML private TextField topKInput;
    @FXML private TextField depthInput;
    @FXML private TextField resultLimitInput;
    @FXML private Button crawlButton;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label statusLabel;
    @FXML private Label totalCrawledLabel;
    @FXML private TableView<PageResult> resultsTable;
    @FXML private TableColumn<PageResult, String> rankCol;
    @FXML private WebView graphWebView;// The Graph Browser
    @FXML private TableColumn<PageResult, String> urlCol;   // ✅ ADDED THIS BACK
    @FXML private TableColumn<PageResult, Double> scoreCol;

    public MainController(CrawlerService crawlerService, PageRankService pageRankService) {
        this.crawlerService = crawlerService;
        this.pageRankService = pageRankService;
    }

    @FXML
    public void initialize() {
        rankCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : "#" + (getIndex() + 1));
            }
        });
        urlCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Hyperlink link = new Hyperlink(item);
                    link.setStyle("-fx-text-fill: #2563eb; -fx-underline: true; -fx-border-color: transparent;");

                    // When the user clicks the link...
                    link.setOnAction(e -> {
                        try {
                            // Make sure it has https:// before asking Windows to open it
                            String targetUri = item.startsWith("http") ? item : "https://" + item;
                            Desktop.getDesktop().browse(new URI(targetUri));
                        } catch (Exception ex) {
                            System.out.println("Could not open browser: " + ex.getMessage());
                        }
                    });
                    setGraphic(link);
                    setText(null);
                }
            }
        });
    }

    @FXML
    public void onStartCrawlClicked() {
        String url = urlInput.getText().trim();
        String topic = topicInput.getText().trim();

        if (url.isEmpty() || topic.isEmpty()) {
            statusLabel.setText("❌ Error: URL and Topic are required.");
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
            return;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        final String targetUrl = url;
        int topK = Integer.parseInt(topKInput.getText().trim());
        int depth = Integer.parseInt(depthInput.getText().trim());
        int resultLimit = Integer.parseInt(resultLimitInput.getText().trim());
        int maxPagesToCrawl = 150;

        // UI Reset
        crawlButton.setDisable(true);
        loadingSpinner.setVisible(true);
        statusLabel.setText("Crawling the web... Please wait.");
        statusLabel.setStyle("-fx-text-fill: #2563eb; -fx-font-weight: bold;");
        totalCrawledLabel.setText("Total Pages Crawled: 0");
        resultsTable.setItems(FXCollections.observableArrayList());

        // Background Thread Setup
        Task<CrawlResponse> crawlTask = new Task<>() {
            @Override
            protected CrawlResponse call() {
                // 1. Run the Crawler Engine
                WebGraph graph = crawlerService.crawl(targetUrl, topic, topK, depth, maxPagesToCrawl);

                // 2. Run PageRank Math
                Map<String, Double> ranks = pageRankService.calculatePageRank(graph);

                // 3. Sort Results
                List<Map.Entry<String, Double>> sortedRanks = new ArrayList<>(ranks.entrySet());
                sortedRanks.sort((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

                // 4. Convert to Table Objects with Limit
                List<PageResult> formattedResults = new ArrayList<>();
                for (int i = 0; i < Math.min(resultLimit, sortedRanks.size()); i++) {
                    Map.Entry<String, Double> entry = sortedRanks.get(i);
                    formattedResults.add(new PageResult(entry.getKey(), entry.getValue()));
                }

                // 5. Protect UI from massive graphs
                Map<String, List<String>> safeGraph = new HashMap<>();
                int edgeCount = 0;
                for (Map.Entry<String, List<String>> entry : graph.getAdjacencyList().entrySet()) {
                    if (edgeCount > 150) break;
                    safeGraph.put(entry.getKey(), entry.getValue());
                    if (entry.getValue() != null) edgeCount += entry.getValue().size();
                }

                return new CrawlResponse(formattedResults, graph.getAdjacencyList().size(), safeGraph);
            }
        };

        // When Thread Finishes
        crawlTask.setOnSucceeded(event -> {
            CrawlResponse response = crawlTask.getValue();

            // Update Table
            ObservableList<PageResult> tableData = FXCollections.observableArrayList(response.tableData);
            resultsTable.setItems(tableData);

            // Update Stats
            totalCrawledLabel.setText("Total Pages Crawled: " + response.totalCrawled);
            statusLabel.setText("✅ System Ready.");
            statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
            resetUI();

            // Render Graph
            try {
                ObjectMapper mapper = new ObjectMapper();
                String jsonGraph = mapper.writeValueAsString(response.graphData);
                renderGraphInWebView(jsonGraph);
            } catch (Exception e) {
                System.out.println("Graph render error: " + e.getMessage());
            }
        });

        // When Thread Fails
        crawlTask.setOnFailed(event -> {
            statusLabel.setText("❌ Error: " + crawlTask.getException().getMessage());
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
            resetUI();
            crawlTask.getException().printStackTrace();
        });

        new Thread(crawlTask).start();
    }

    private void resetUI() {
        crawlButton.setDisable(false);
        loadingSpinner.setVisible(false);
    }

    // --- Graph Rendering Method ---
    private void renderGraphInWebView(String jsonAdjacencyList) {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <script type="text/javascript" src="https://unpkg.com/vis-network/standalone/umd/vis-network.min.js"></script>
                <style>
                    body { margin: 0; padding: 0; background-color: #fafafa; font-family: sans-serif; }
                    #network { width: 100vw; height: 100vh; }
                </style>
            </head>
            <body>
                <div id="network"></div>
                <script>
                    const adjacencyList = %s;
                    const nodesArray = [];
                    const edgesArray = [];
                    const nodeSet = new Set();
                    
                    function shorten(url) {
                        try {
                            let u = new URL(url);
                            let path = u.pathname;
                            if (path === '/' || path === '') return u.hostname;
                            return path.length > 20 ? path.substring(0, 20) + '...' : path;
                        } catch(e) { return url.substring(0, 20); }
                    }

                    for (const source in adjacencyList) {
                        if (!nodeSet.has(source)) {
                            nodesArray.push({ id: source, label: shorten(source), title: source });
                            nodeSet.add(source);
                        }
                        const targets = adjacencyList[source];
                        if (targets) {
                            targets.forEach(target => {
                                if (!nodeSet.has(target)) {
                                    nodesArray.push({ id: target, label: shorten(target), title: target });
                                    nodeSet.add(target);
                                }
                                edgesArray.push({ from: source, to: target, arrows: 'to' });
                            });
                        }
                    }

                    const container = document.getElementById('network');
                    const data = { nodes: new vis.DataSet(nodesArray), edges: new vis.DataSet(edgesArray) };
                    const options = {
                        nodes: { shape: 'dot', size: 12, color: { background: '#3b82f6', border: '#2563eb' }, font: {size: 10} },
                        edges: { color: '#cbd5e1', width: 1 },
                        physics: { barnesHut: { springLength: 80, avoidOverlap: 0.1 } },
                        interaction: { hover: true }
                    };
                    new vis.Network(container, data, options);
                </script>
            </body>
            </html>
            """.formatted(jsonAdjacencyList);

        Platform.runLater(() -> {
            WebEngine webEngine = graphWebView.getEngine();
            webEngine.loadContent(html);
        });
    }

    // --- Helper Classes ---
    private static class CrawlResponse {
        List<PageResult> tableData;
        int totalCrawled;
        Map<String, List<String>> graphData;

        public CrawlResponse(List<PageResult> tableData, int totalCrawled, Map<String, List<String>> graphData) {
            this.tableData = tableData;
            this.totalCrawled = totalCrawled;
            this.graphData = graphData;
        }
    }

    public static class PageResult {
        private final String url;
        private final double score;

        public PageResult(String url, double score) {
            this.url = url;
            this.score = Math.round(score * 10000.0) / 10000.0;
        }

        public String getUrl() { return url; }
        public double getScore() { return score; }
    }
}