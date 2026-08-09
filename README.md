# 🕸️ WebWeaver: Heuristic-Guided Web Crawler

WebWeaver is a high-performance, multithreaded desktop application designed to intelligently crawl, score, and visualize the web. Instead of using a standard "blind" Breadth-First Search (BFS) that blindly downloads the internet, WebWeaver uses an A*-inspired heuristic engine to evaluate links on the fly, pruning irrelevant pages and prioritizing highly relevant paths.

The application features a modern JavaFX dashboard, an embedded interactive physics graph (Vis.js), and a persistent SQLite database.

---

## ✨ Key Features

* **Intelligent Heuristic Engine:** Evaluates URLs and anchor text using exact phrase matching, keyword tokenization, and structural penalties (filtering out login, privacy, and cart pages).
* **High-Speed Multithreading:** Utilizes a custom Thread Pool (`ExecutorService`) with Level-Order Batching and concurrent data structures to crawl multiple pages simultaneously without race conditions.
* **PageRank Integration:** Calculates the mathematical relevance of nodes based on their interconnections within the localized web graph.
* **Interactive Visualization:** Embeds a Chromium-based `WebView` to render a dynamic, drag-and-drop Vis.js network graph of the crawled web structure.
* **SQLite Persistence:** Automatically saves crawled pages, relevance scores, and edge relationships to a local serverless database using Spring JDBC.
* **Modern JavaFX Dashboard:** A sleek, fully native desktop UI with clickable data tables, soft-shadow cards, and real-time statistic tracking.

---

## 🛠️ Tech Stack

**Backend Engine:**
* Java 21
* Spring Boot (Dependency Injection & Context Management)
* Jsoup (HTML Parsing & DOM Pruning)
* Java `ExecutorService` & `Concurrent` Collections (Multithreading)

**Data Persistence:**
* SQLite
* Spring JDBC

**Frontend / UI:**
* JavaFX (FXML, CSS)
* JavaFX WebEngine (Embedded WebKit Browser)
* HTML5 / JavaScript / Vis.js (Graph Rendering)

---

## 🧠 System Architecture

The project was built in sequential architectural phases:

1. **The Core Engine (Heuristic Search):**
   Implemented DOM pruning to strip heavy HTML elements. Uses Beam Search to strictly enforce a "Top-K" limit per page, ensuring the crawler only follows the most relevant links based on a calculated score.
2. **The Memory (Persistence):**
   Integrated SQLite to ensure the scraped web graph is permanently stored. Tables are auto-initialized on startup, utilizing thread-safe SQL `INSERT OR IGNORE` queries.
3. **The Face (JavaFX Transition):**
   Bridged the Spring Boot backend with a JavaFX frontend. Disabled Spring's "Headless Mode" to allow native desktop OS integrations (like opening clickable links in the system's default browser).
4. **The Muscle (Multithreading):**
   Replaced sequential downloading with a 10-thread parallel processing engine. Utilizes `ConcurrentLinkedQueue`, `ConcurrentHashMap`, and targeted `synchronized` graph locking to ensure thread safety while maximizing network I/O speed.

---

## 🚀 How to Run

### Prerequisites
* **Java 21** or higher
* **Maven** (for dependency management)
* An IDE like **IntelliJ IDEA** or **Eclipse**

### Execution
1. Clone the repository to your local machine.
2. Open the project in your IDE and allow Maven to download all dependencies (ensure `javafx-web` is synced).
3. Locate the main application class: `src/main/java/com/akatsuki/webweaver/JavaFxApplication.java` (or run your Spring Boot `WebweaverApplication.class` depending on your run configuration).
4. Run the application.

### Usage Guide
1. **Start URL:** Enter a seed URL (e.g., `en.wikipedia.org/wiki/Music`). *Auto-HTTPS will handle missing protocols.*
2. **Topic:** Enter the subject you are targeting (e.g., `rock music`).
3. **Top-K:** The maximum number of relevant links the crawler will follow per page.
4. **Depth:** How many clicks deep the crawler is allowed to travel from the seed URL.
5. **Result Limit:** Limits the number of results rendered in the UI data table.
6. Click **START AI CRAWL**. The system will lock the UI, spin up the background threads, and render the final table and graph once complete.

---

## ⚠️ Important Notes
* **Bot Protections:** Some modern websites (like major news outlets) utilize aggressive anti-bot protections (e.g., Cloudflare) that return `403 Forbidden` errors. WebWeaver is designed to automatically catch these exceptions, drop the uncooperative node, and seamlessly continue crawling other paths.
* **Graph Rendering:** To prevent UI freezing, the interactive Vis.js graph has a hardcoded safety limit of 150 edges. The backend engine and database will still process and save the entire graph beyond this limit.