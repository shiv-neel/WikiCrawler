import java.util.ArrayList;

public class Queue<T> {

    private ArrayList<T> queue;

    public Queue() {
        this.queue = new ArrayList<>();
    }

    public void enqueue(T item) {
        this.queue.add(item);
    }

    public T dequeue() {
        return this.queue.remove(0);
    }

    public boolean isEmpty() {
        return this.queue.isEmpty();
    }

    public boolean contains(T item) {
        return this.queue.contains(item);
    }
}
