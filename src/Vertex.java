public class Vertex {

    private String url;
    public Vertex(String url) {
        this.url = url;
    }

    public String getUrl() {
        return this.url;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Vertex)) {
            return false;
        }
        Vertex v = (Vertex) o;
        return this.url.equals(v.getUrl());
    }

    @Override
    public int hashCode() {
        return this.url.hashCode();
    }
}
