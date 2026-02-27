import java.util.*;

public class Simulation {

    private Random rng = new Random();

    private int numServers;
    private double simEndTime;           // minutes
    private double lambdaPerMinute;      // arrival rate per minute
    private double meanServiceTime;      // mean service time in minutes

    private double clock = 0.0;

    private PriorityQueue<Event> eventList;
    private CustomerQueue queue = new CustomerQueue();
    private ArrayList<Customer> customers = new ArrayList<>();
    private Server[] servers;

    private double lastEventTime = 0.0;
    private double areaL = 0.0;
    private double areaLq = 0.0;
    private int maxQueueLength = 0;

    private int totalArrivals = 0;
    private int totalServed = 0;

    public Simulation(int numServers, double hours, double lambdaPerHour, double meanServiceTime) {
        this.numServers = numServers;
        this.simEndTime = hours * 60.0;
        this.lambdaPerMinute = lambdaPerHour / 60.0;
        this.meanServiceTime = meanServiceTime;

        eventList = new PriorityQueue<>(Comparator.comparingDouble(e -> e.time));

        servers = new Server[numServers];
        for (int i = 0; i < numServers; i++) {
            servers[i] = new Server(i);
        }
    }

    // Generates exponential interarrival time
    private double sampleInterarrival() {
        double u = rng.nextDouble();
        if (u == 0.0) u = 0.000001;
        return -Math.log(u) / lambdaPerMinute;
    }

    // Generates exponential service time
    private double sampleServiceTime() {
        double u = rng.nextDouble();
        if (u == 0.0) u = 0.000001;
        return -Math.log(u) * meanServiceTime;
    }

    private int findFreeServer() {
        for (int i = 0; i < servers.length; i++) {
            if (!servers[i].busy) return i;
        }
        return -1;
    }

    private void updateTimeAverages(double eventTime) {
        double delta = eventTime - lastEventTime;

        int inService = 0;
        for (Server s : servers) {
            if (s.busy) inService++;
        }

        int queueLength = queue.size();
        int inSystem = queueLength + inService;

        areaL += inSystem * delta;
        areaLq += queueLength * delta;

        lastEventTime = eventTime;
    }

    private void scheduleFirstArrival() {
        double time = sampleInterarrival();
        eventList.add(new Event(time, EventType.ARRIVAL, 0, -1));
    }

    private void scheduleNextArrival(int nextCustomerId) {
        double time = clock + sampleInterarrival();
        if (time <= simEndTime) {
            eventList.add(new Event(time, EventType.ARRIVAL, nextCustomerId, -1));
        }
    }

    private void startService(int customerId, int serverId) {
        Customer customer = customers.get(customerId);
        customer.serviceStartTime = clock;

        double serviceTime = sampleServiceTime();

        servers[serverId].startService(customerId, clock, serviceTime);

        eventList.add(new Event(clock + serviceTime, EventType.DEPARTURE, customerId, serverId));
    }

    private void handleArrival(Event event) {
        totalArrivals++;

        int customerId = customers.size();
        customers.add(new Customer(customerId, clock));

        scheduleNextArrival(customerId + 1);

        int freeServer = findFreeServer();

        if (freeServer != -1) {
            startService(customerId, freeServer);
        } else {
            queue.enqueue(customerId);
            if (queue.size() > maxQueueLength) {
                maxQueueLength = queue.size();
            }
        }
    }

    private void handleDeparture(Event event) {
        totalServed++;

        int serverId = event.serverId;
        int customerId = event.customerId;

        Customer customer = customers.get(customerId);
        customer.departureTime = clock;

        servers[serverId].endService();

        if (!queue.isEmpty()) {
            int nextCustomerId = queue.dequeue();
            startService(nextCustomerId, serverId);
        }
    }

    public void run() {
        scheduleFirstArrival();

        while (!eventList.isEmpty()) {

            Event event = eventList.poll();

            if (event.time > simEndTime) break;

            updateTimeAverages(event.time);

            clock = event.time;

            if (event.type == EventType.ARRIVAL) {
                handleArrival(event);
            } else {
                handleDeparture(event);
            }
        }

        updateTimeAverages(simEndTime);
        clock = simEndTime;
    }

    public void printResults() {

        double totalWait = 0.0;
        double totalSystemTime = 0.0;
        double maxWait = 0.0;

        for (Customer c : customers) {
            if (c.serviceStartTime != null && c.departureTime != null) {
                double wait = c.getWaitTime();
                double systemTime = c.getTimeInSystem();

                totalWait += wait;
                totalSystemTime += systemTime;

                if (wait > maxWait) {
                    maxWait = wait;
                }
            }
        }

        double avgWait = totalServed > 0 ? totalWait / totalServed : 0.0;
        double avgSystemTime = totalServed > 0 ? totalSystemTime / totalServed : 0.0;

        double avgL = areaL / simEndTime;
        double avgLq = areaLq / simEndTime;

        double totalBusy = 0.0;
        for (Server s : servers) {
            totalBusy += s.busyTime;
        }

        double utilization = totalBusy / (numServers * simEndTime);

        double predictedL = lambdaPerMinute * avgSystemTime;

        System.out.println("===== Simulation Results =====");
        System.out.println("Total Arrivals: " + totalArrivals);
        System.out.println("Total Served: " + totalServed);

        System.out.println("Average Wait Time: " + avgWait);
        System.out.println("Max Wait Time: " + maxWait);
        System.out.println("Average Time in System: " + avgSystemTime);

        System.out.println("Average Queue Length (Lq): " + avgLq);
        System.out.println("Max Queue Length: " + maxQueueLength);
        System.out.println("Average Number in System (L): " + avgL);

        System.out.println("Overall Utilization: " + utilization);

        System.out.println("Little's Law Check:");
        System.out.println("Observed L: " + avgL);
        System.out.println("Predicted λW: " + predictedL);
    }

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.println("Restaurant Service Simulation (Discrete Event)");

        System.out.print("Enter number of servers: ");
        int servers = Integer.parseInt(sc.nextLine());

        System.out.print("Enter simulation duration (hours): ");
        double hours = Double.parseDouble(sc.nextLine());

        System.out.print("Enter arrival rate (customers per hour): ");
        double lambda = Double.parseDouble(sc.nextLine());

        System.out.print("Enter average service time (minutes): ");
        double meanService = Double.parseDouble(sc.nextLine());

        Simulation simulation = new Simulation(servers, hours, lambda, meanService);

        System.out.println("Simulation running...");
        simulation.run();
        simulation.printResults();
    }
}