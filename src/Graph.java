import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Graph {

    private HashMap<Vertex, ArrayList<Vertex>> graph;

    public Graph() {
        this.graph = new HashMap<>();
    }

    public void addVertex(Vertex v) {
        if (this.graph.containsKey(v)) {
            throw new IllegalArgumentException("Vertex already in graph");
        }
        this.graph.put(v, new ArrayList<>());
    }

    public void addOutgoingEdge(Vertex v1, Vertex v2) {
        if (!this.graph.containsKey(v1)) {
            throw new IllegalArgumentException("Vertex not in graph");
        }
        this.graph.get(v1).add(v2);
    }

    public List<Vertex> getNeighbors(Vertex v) {
        if (!this.graph.containsKey(v)) {
            throw new IllegalArgumentException("Vertex not in graph");
        }
        return new ArrayList<>(this.graph.get(v));
    }

    public int getOutdegree(Vertex v) {
        if (!this.graph.containsKey(v)) {
            throw new IllegalArgumentException("Vertex not in graph");
        }
        return this.graph.get(v).size();
    }

    public List<Vertex> getVertices() {
        return new ArrayList<>(this.graph.keySet());
    }
}
