import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class that crawls Wikipedia
 */
public class WikiCrawler {

    public static final String BASE_URL = "https://en.wikipedia.org";

    private List<String> keywords;

    private int max;

    private String fileName;

    /**
     * maps each URL to outgoing links
     */
    private Graph graph;

    private Set<String> visitedUrls;

    private Queue<Vertex> queue;

    private HashSet<String> disallowedUrls = new HashSet<>();

    private int numRequests;

    public WikiCrawler(
            String seedUrl, String[] keywords, int max, String fileName) {
        this.keywords = Arrays.asList(keywords);
        this.max = max;
        this.fileName = fileName;

        this.graph = new Graph();
        this.visitedUrls = new HashSet<>();
        this.queue = new Queue<>();

        Vertex root = new Vertex(seedUrl);

        this.graph.addVertex(root);
        this.queue.enqueue(root);

        this.numRequests = 0;

        try {
            this.fetchDisallowedUrls();
            this.crawl();
        } catch (Exception e) {
            System.out.println("Could not fetch disallowed URLs from robots.txt");
            Logger.log(e.getMessage());
        }
    }


    /**
     * fetches disallowed URLs from <a href="https://en.wikipedia.org/robots.txt"></a>
     * @throws Exception e
     */
    public void fetchDisallowedUrls() throws Exception {
        URL url = new URL(BASE_URL + "/robots.txt");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Disallow")) {
                    if (line.split(":").length < 2) {
                        continue;
                    }
                    String disallowedUrl = line.split(":")[1].trim();
                    this.disallowedUrls.add(disallowedUrl);
                }
            }
            Logger.log("Fetched disallowed URLs from " + BASE_URL + "/robots.txt");
        }
        catch (Exception e) {
            System.out.println("Could not fetch disallowed URLs from robots.txt");
        }
    }

    /**
     * starts crawling from seedUrl
     * explores up to `this.max` pages
     * writes the graph to the file `this.fileName`
     */
    void crawl() throws Exception {

        while (!this.queue.isEmpty() && this.visitedUrls.size() < this.max) {
            Vertex v = this.queue.dequeue();
            if (this.visitedUrls.contains(v)) {
                continue;
            }
            this.visitedUrls.add(v.getUrl());

            try {
                String html = fetchHTML(v.getUrl());
                List<String> outgoingLinks = this.extractLinks(html);
                String pageContent = this.extractTextContent(html);

                for (String link : outgoingLinks) {
                    if (this.graph.getVertices().size() >= this.max) break;

                    if (this.numRequests >= 10) {
                        Logger.log("\033[0;33m" + "Sleeping for 1 second to obey politeness policy" + "\033[0m");
                        TimeUnit.SECONDS.sleep(1);
                        this.numRequests = 0;
                    }
                    if (this.isRelevantPage(link, pageContent)) {
                        Vertex newVertex = new Vertex("/wiki/" + link.toLowerCase());

                        if (!this.visitedUrls.contains(newVertex)
                                && !this.queue.contains(newVertex)
                                && this.graph.getVertices().size() < this.max) {
                            this.graph.addVertex(newVertex);
                            this.graph.addOutgoingEdge(v, newVertex);
                            this.queue.enqueue(newVertex);
                        }
                    }

                    this.numRequests++;
                }
            } catch (Exception e) {
                System.out.println("Could not fetch HTML for " + v.getUrl());
                Logger.log(e.getMessage());
            }

        }
        writeToFile();
    }

    void crawl() throws Exception {
        while (!this.queue.isEmpty() && this.graph.getVertices().size() < max) {
            Vertex v = this.queue.dequeue();
            String url = v.getUrl();

            if (this.disallowedUrls.contains(url)) {
                Logger.log("\033[0;31m Url is disallowed: " + url + "\033[0m");
            }

            if (this.visitedUrls.contains(url)) {
                Logger.log("\033[0;35m Already visited url: " + url + "\033[0m");
            }

            this.visitedUrls.add(url);


            String html = this.fetchHTML(url);
            List<String> links = this.extractLinks(html);

            Logger.log(" - Found " + links.size() + " links on this page");

            for (String link : links) {
                this.graph.addOutgoingEdge(new Vertex(url), new Vertex(link));
                this.queue.enqueue(new Vertex(link));
            }

            this.numRequests++;
            if (this.numRequests >= 10) {
                Logger.log("\033[0;33m" + "Sleeping for 1 second to obey politeness policy" + "\033[0m");
                TimeUnit.SECONDS.sleep(1);
                this.numRequests = 0;
            }

            Logger.log(" - " + this.visitedUrls.size() + " pages visited");

            this.graph.removeEdgesToNonVisitedVertices(this.visitedUrls);

            Logger.log(" - " + this.graph.getVertices().size() + " vertices in graph");

            writeToFile();
        }
    }
    public String fetchHTML(String pageUrl) throws Exception {
        StringBuilder result = new StringBuilder();
        URL url = new URL(BASE_URL + pageUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }

    private List<String> extractLinks(String html) {
        List<String> links = new ArrayList<>();
        Pattern pTagPattern = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL);
        Pattern linkPattern = Pattern.compile("<a href=\"/wiki/(.*?)\"", Pattern.DOTALL);

        Matcher pTagMatcher = pTagPattern.matcher(html);
        while (pTagMatcher.find()) {
            String pContent = pTagMatcher.group(1);
            Matcher linkMatcher = linkPattern.matcher(pContent);
            while (linkMatcher.find()) {
                links.add(linkMatcher.group(1));
            }
        }
        return links;
    }

    private String extractTextContent(String html) {
        StringBuilder textContent = new StringBuilder();
        Pattern pTagPattern = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL);
        Matcher pTagMatcher = pTagPattern.matcher(html);

        while (pTagMatcher.find()) {
            textContent.append(pTagMatcher.group(1));
        }

        return textContent.toString();
    }

    private boolean isRelevantLink(String link, String pageContent) {
        Logger.log(this.graph.getVertices().size() + "");
        // Logger.log("Checking if " + link + " is relevant");
        boolean isDuplicate = this.graph.getVertices().contains(new Vertex(link));
        boolean isInRobotsTxt = this.disallowedUrls.contains(link);
        boolean isFragment = link.contains("#");
        boolean isSpecialPage = link.contains(":");

        if (isDuplicate || isInRobotsTxt || isFragment || isSpecialPage) {
            return false;
        }
        int relevancyScore = 0;
        int threshold = 1; // You can adjust this threshold based on your needs

        // Check if link's anchor text contains keywords
        Pattern anchorTextPattern = Pattern.compile("<a href=\"/wiki/" + Pattern.quote(link) + "\"[^>]*>(.*?)</a>");
        Matcher anchorTextMatcher = anchorTextPattern.matcher(pageContent);
        while (anchorTextMatcher.find()) {
            String anchorText = anchorTextMatcher.group(1);
            for (String keyword : this.keywords) {
                if (anchorText.toLowerCase().contains(keyword.toLowerCase())) {
                    relevancyScore++;
                    break; // Break after finding the first keyword to avoid over-counting
                }
            }
        }

        // Check the surrounding text of the link for keywords
        Pattern surroundingTextPattern = Pattern.compile("([\\s\\S]{0,100})<a href=\"/wiki/" + Pattern.quote(link) + "\"[^>]*>[\\s\\S]{0,100}");
        Matcher surroundingTextMatcher = surroundingTextPattern.matcher(pageContent);
        while (surroundingTextMatcher.find()) {
            String surroundingText = surroundingTextMatcher.group(1);
            for (String keyword : this.keywords) {
                if (surroundingText.contains(keyword)) {
                    relevancyScore++;
                    break; // Break after finding the first keyword to avoid over-counting
                }
            }
        }

        return relevancyScore >= threshold;
    }

    private boolean isRelevantPage(String link, String __) {
        Logger.log(this.graph.getVertices().size() + "");

        boolean isDuplicate = this.graph.getVertices().contains(new Vertex(link));
        boolean isInRobotsTxt = this.disallowedUrls.contains(link);
        boolean isFragment = link.contains("#");
        boolean isSpecialPage = link.contains(":");

        if (isDuplicate || isInRobotsTxt || isFragment || isSpecialPage) {
            return false;
        }
        for (String keyword : this.keywords) {
            if (link.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * writes the graph to the file `this.fileName`
     */
    private void writeToFile() {
        try {
            PrintWriter writer = new PrintWriter(this.fileName);
            writer.println(this.graph.getVertices().size());

            this.graph.getVertices().forEach(v -> writer.println(v.getUrl()));

            writer.close();
            Logger.log("Wrote graph to file " + this.fileName);
        } catch (IOException e) {
            System.out.println("Could not write to file " + this.fileName);
        }
    }

    public static void main(String[] args) throws Exception {
        String[] keywords = {"tennis", "grand slam"};
        WikiCrawler crawler = new WikiCrawler(
                "/wiki/Tennis", keywords, 20, "Tennis1.txt");

        crawler.crawl();
    }
}