import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private Set<String> visitedVertices;

    private Queue<Vertex> queue;

    private HashSet<String> disallowedUrls = new HashSet<>();

    public WikiCrawler(
            String seedUrl, String[] keywords, int max, String fileName) {
        this.keywords = Arrays.asList(keywords);
        this.max = max;
        this.fileName = fileName;

        this.graph = new Graph();
        this.visitedVertices = new HashSet<>();
        this.queue = new Queue<>();

        Vertex root = new Vertex(seedUrl);

        this.graph.addVertex(root);
        this.queue.enqueue(root);

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

        while (!this.queue.isEmpty() && this.graph.getVertices().size() < this.max) {
            Vertex v = this.queue.dequeue();
            if (this.visitedVertices.contains(v.getUrl())) {
                continue;
            }
            this.visitedVertices.add(v.getUrl());

            try {
                String html = fetchHTML("/wiki/" + v.getUrl());
                List<String> outgoingLinks = extractLinks(html);

                List<String> filteredLinks = outgoingLinks.stream().filter(this::isRelevantPage).toList();

                for (String link : filteredLinks) {
                    Vertex newVertex = new Vertex(link);
                    if (!this.visitedVertices.contains(link) && !this.queue.contains(newVertex) && this.graph.getVertices().size() < this.max) {
                        this.graph.addVertex(newVertex);
                        this.graph.addOutgoingEdge(v, newVertex);
                        this.queue.enqueue(newVertex);
                    }
                }
            } catch (Exception e) {
                System.out.println("Could not fetch HTML for " + v.getUrl());
                Logger.log(e.getMessage());
            }

        }
        writeToFile();
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

    private boolean isRelevantPage(String url) {
        boolean isDuplicate =
                this.visitedVertices.contains(url) || this.graph.getVertices().contains(new Vertex(url));
        boolean isInRobotsTxt = this.disallowedUrls.contains(url);
        boolean isFragment = url.contains("#");
        boolean isSpecialPage = url.contains(":");

        if (isDuplicate || isInRobotsTxt || isFragment || isSpecialPage) {
            return false;
        }

        for (String keyword : this.keywords) {
            if (url.contains(keyword)) {
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
        String[] keywords = {"Communism", "Capitalism"};
        WikiCrawler crawler = new WikiCrawler(
                "/wiki/Marxism", keywords, 50, "Marxism.txt");

        String html = crawler.fetchHTML("/wiki/Marxism");
        List<String> links = crawler.extractLinks(html);
        List<String> filteredLinks = links.stream().filter(crawler::isRelevantPage).toList();
        System.out.println(filteredLinks.size() + ", " + filteredLinks);
    }
}