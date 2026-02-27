import java.util.ArrayDeque;

public class CustomerQueue {
    private ArrayDeque<Integer> queue;

    public CustomerQueue() {
        queue = new ArrayDeque<>();
    }

    // adds to FIFO queue
    public void enqueue(int customerId) {
        queue.addLast(customerId);
    }

    // removes from FIFO queue
    public int dequeue() {
        return queue.removeFirst();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }
}