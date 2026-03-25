import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;

public class Simulation {

    private Random rng = new Random();

    private int numServers;
    private double simEndTime;              // minutes
    private double lambdaPerMinute;         // arrival rate per minute
    private double meanServiceTime;         // mean service time in minutes
    private String runId;
    private double waitTimeLimit;           // user input in minutes

    private double clock = 0.0;

    private PriorityQueue<Event> eventList;
    private CustomerQueue queue = new CustomerQueue();
    private ArrayList<Customer> customers = new ArrayList<>();
    private Server[] servers;

    private double lastEventTime = 0.0;
    private double areaL = 0.0;
    private double areaLq = 0.0;
    private double timeQueueNonEmpty = 0.0;
    private double[] serverBusyArea;
    private int maxQueueLength = 0;

    private int totalArrivals = 0;
    private int totalServed = 0;

    private double totalWait = 0.0;
    private double totalSystemTime = 0.0;
    private double maxWait = 0.0;
    private int waitAboveLimitCount = 0;
    private int zeroWaitCount = 0;

    private double nextSnapshotTime = 60.0;
    private int arrivalsThisHour = 0;
    private int servedThisHour = 0;
    private double lastSnapshotTime = 0.0;

    private PrintWriter timeSeriesWriter;
    private PrintWriter summaryWriter;

    public Simulation(int numServers, double hours, double lambdaPerHour,
                      double meanServiceTime, String runId, double waitTimeLimit) {

        this.numServers = numServers;
        this.simEndTime = hours * 60.0;
        this.lambdaPerMinute = lambdaPerHour / 60.0;
        this.meanServiceTime = meanServiceTime;
        this.runId = runId.trim().replaceAll("\\s+", "_");
        this.waitTimeLimit = waitTimeLimit;

        eventList = new PriorityQueue<>(Comparator.comparingDouble(e -> e.time));

        servers = new Server[numServers];
        serverBusyArea = new double[numServers];

        for (int i = 0; i < numServers; i++) {
            servers[i] = new Server(i + 1);
        }

        initializeCsvFiles();
    }

    private void initializeCsvFiles() {
        try {
            timeSeriesWriter = new PrintWriter(new FileWriter("run_" + runId + "_timeseries.csv"));
            summaryWriter = new PrintWriter(new FileWriter("run_" + runId + "_summarymetrics.csv"));

            timeSeriesWriter.println(
                    "Time Minutes,Queue Length,Customers In System,Overall Utilization Percent,Average Wait Time Minutes,Customers Served This Hour,Customers Arrived This Hour"
            );

            summaryWriter.println("Metric,Value");

        } catch (IOException e) {
            System.out.println("Error creating CSV files: " + e.getMessage());
            timeSeriesWriter = null;
            summaryWriter = null;
        }
    }

    private double sampleInterarrival() {
        double u = rng.nextDouble();
        if (u == 0.0) {
            u = 0.000001;
        }
        return -Math.log(u) / lambdaPerMinute;
    }

    private double sampleServiceTime() {
        double u = rng.nextDouble();
        if (u == 0.0) {
            u = 0.000001;
        }
        return -Math.log(u) * meanServiceTime;
    }

    private int findFreeServer() {
        for (int i = 0; i < servers.length; i++) {
            if (!servers[i].busy) {
                return i;
            }
        }
        return -1;
    }

    private void updateTimeAverages(double eventTime) {
        double delta = eventTime - lastEventTime;

        int inService = 0;
        for (int i = 0; i < servers.length; i++) {
            if (servers[i].busy) {
                inService++;
                serverBusyArea[i] += delta;
            }
        }

        int queueLength = queue.size();
        int inSystem = queueLength + inService;

        areaL += inSystem * delta;
        areaLq += queueLength * delta;

        if (queueLength > 0) {
            timeQueueNonEmpty += delta;
        }

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
        arrivalsThisHour++;

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
        servedThisHour++;

        int serverId = event.serverId;
        int customerId = event.customerId;

        Customer customer = customers.get(customerId);
        customer.departureTime = clock;

        double wait = customer.getWaitTime();
        double systemTime = customer.getTimeInSystem();

        totalWait += wait;
        totalSystemTime += systemTime;

        if (wait > maxWait) {
            maxWait = wait;
        }

        if (wait > waitTimeLimit) {
            waitAboveLimitCount++;
        }

        if (wait == 0.0) {
            zeroWaitCount++;
        }

        servers[serverId].endService();

        if (!queue.isEmpty()) {
            int nextCustomerId = queue.dequeue();
            startService(nextCustomerId, serverId);
        }
    }

    private void writeTimeSeriesSnapshot(double snapshotTime) {
        if (timeSeriesWriter == null) {
            return;
        }

        int inService = 0;
        for (Server s : servers) {
            if (s.busy) {
                inService++;
            }
        }

        int queueLength = queue.size();
        int inSystem = queueLength + inService;

        double totalBusy = 0.0;
        for (double busy : serverBusyArea) {
            totalBusy += busy;
        }

        double overallUtilization = snapshotTime > 0
                ? (totalBusy / (numServers * snapshotTime)) * 100.0
                : 0.0;

        double averageWait = totalServed > 0 ? totalWait / totalServed : 0.0;

        timeSeriesWriter.printf(
                Locale.US,
                "%.0f,%d,%d,%.2f,%.2f,%d,%d%n",
                snapshotTime,
                queueLength,
                inSystem,
                overallUtilization,
                averageWait,
                servedThisHour,
                arrivalsThisHour
        );

        lastSnapshotTime = snapshotTime;
        arrivalsThisHour = 0;
        servedThisHour = 0;
    }

    private void processHourlySnapshots(double upToTime) {
        while (nextSnapshotTime <= upToTime && nextSnapshotTime <= simEndTime) {
            updateTimeAverages(nextSnapshotTime);
            writeTimeSeriesSnapshot(nextSnapshotTime);
            nextSnapshotTime += 60.0;
        }
    }

    private void writeFinalSnapshot() {
        if (timeSeriesWriter == null) {
            return;
        }

        if (Math.abs(lastSnapshotTime - simEndTime) > 0.0001) {
            int inService = 0;
            for (Server s : servers) {
                if (s.busy) {
                    inService++;
                }
            }

            int queueLength = queue.size();
            int inSystem = queueLength + inService;

            double totalBusy = 0.0;
            for (double busy : serverBusyArea) {
                totalBusy += busy;
            }

            double overallUtilization = simEndTime > 0
                    ? (totalBusy / (numServers * simEndTime)) * 100.0
                    : 0.0;

            double averageWait = totalServed > 0 ? totalWait / totalServed : 0.0;

            timeSeriesWriter.printf(
                    Locale.US,
                    "%.0f,%d,%d,%.2f,%.2f,%d,%d%n",
                    simEndTime,
                    queueLength,
                    inSystem,
                    overallUtilization,
                    averageWait,
                    servedThisHour,
                    arrivalsThisHour
            );

            lastSnapshotTime = simEndTime;
        }
    }

    public void run() {
        scheduleFirstArrival();

        while (!eventList.isEmpty()) {
            Event event = eventList.poll();

            if (event.time > simEndTime) {
                break;
            }

            processHourlySnapshots(event.time);

            updateTimeAverages(event.time);
            clock = event.time;

            if (event.type == EventType.ARRIVAL) {
                handleArrival(event);
            } else {
                handleDeparture(event);
            }
        }

        processHourlySnapshots(simEndTime);
        updateTimeAverages(simEndTime);
        clock = simEndTime;

        writeFinalSnapshot();
    }

    private String f2(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private void writeSummaryCsv(double avgWait,
                                 double avgSystemTime,
                                 double avgQueueLength,
                                 double avgCustomersInSystem,
                                 double timeQueueNonEmptyPercent,
                                 double overallUtilization,
                                 double[] serverUtilization,
                                 double percentWaitAboveLimit,
                                 double percentZeroWait,
                                 double trafficIntensity,
                                 String systemStability,
                                 double predictedL) {

        if (summaryWriter == null) {
            return;
        }

        summaryWriter.println("Run ID," + runId);

        summaryWriter.println("Average Wait Time (minutes)," + f2(avgWait));
        summaryWriter.println("Maximum Wait Time (minutes)," + f2(maxWait));
        summaryWriter.println("Percent Exceeding Wait Time Limit," + f2(percentWaitAboveLimit));
        summaryWriter.println("Percent With Zero Wait," + f2(percentZeroWait));
        summaryWriter.println("Wait Time Limit (minutes)," + f2(waitTimeLimit));
        summaryWriter.println("Total Customers Arrived," + totalArrivals);
        summaryWriter.println("Total Customers Served," + totalServed);

        summaryWriter.println("Average Queue Length," + f2(avgQueueLength));
        summaryWriter.println("Maximum Queue Length," + maxQueueLength);
        summaryWriter.println("Time Queue Was Non Empty (minutes)," + f2(timeQueueNonEmpty));
        summaryWriter.println("Time Queue Was Non Empty (percent)," + f2(timeQueueNonEmptyPercent));
        summaryWriter.println("Average Customers In System," + f2(avgCustomersInSystem));
        summaryWriter.println("Average Time In System (minutes)," + f2(avgSystemTime));

        summaryWriter.println("Overall Utilization (percent)," + f2(overallUtilization));
        for (int i = 0; i < serverUtilization.length; i++) {
            summaryWriter.println("Server " + (i + 1) + " Utilization (percent)," + f2(serverUtilization[i]));
        }

        summaryWriter.println("Traffic Intensity," + f2(trafficIntensity));
        summaryWriter.println("System Stability," + systemStability);
        summaryWriter.println("Little's Law Observed L," + f2(avgCustomersInSystem));
        summaryWriter.println("Little's Law Predicted Lambda W," + f2(predictedL));
    }

    public void printResults() {
        double avgWait = totalServed > 0 ? totalWait / totalServed : 0.0;
        double avgSystemTime = totalServed > 0 ? totalSystemTime / totalServed : 0.0;

        double avgCustomersInSystem = simEndTime > 0 ? areaL / simEndTime : 0.0;
        double avgQueueLength = simEndTime > 0 ? areaLq / simEndTime : 0.0;

        double totalBusy = 0.0;
        double[] serverUtilization = new double[numServers];

        for (int i = 0; i < numServers; i++) {
            totalBusy += serverBusyArea[i];
            serverUtilization[i] = simEndTime > 0
                    ? (serverBusyArea[i] / simEndTime) * 100.0
                    : 0.0;
        }

        double overallUtilization = simEndTime > 0
                ? (totalBusy / (numServers * simEndTime)) * 100.0
                : 0.0;

        double percentWaitAboveLimit = totalServed > 0
                ? ((double) waitAboveLimitCount / totalServed) * 100.0
                : 0.0;

        double percentZeroWait = totalServed > 0
                ? ((double) zeroWaitCount / totalServed) * 100.0
                : 0.0;

        double timeQueueNonEmptyPercent = simEndTime > 0
                ? (timeQueueNonEmpty / simEndTime) * 100.0
                : 0.0;

        double mu = meanServiceTime > 0 ? (1.0 / meanServiceTime) : 0.0;
        double trafficIntensity = (numServers > 0 && mu > 0)
                ? (lambdaPerMinute / (numServers * mu))
                : 0.0;

        String systemStability = trafficIntensity < 1.0 ? "Stable" : "Unstable";

        double predictedL = lambdaPerMinute * avgSystemTime;

        System.out.println();
        System.out.println("==================================================");
        System.out.println("Discrete Event Restaurant Simulation");
        System.out.println("==================================================");
        System.out.println("Simulation complete. Printing metrics...");
        System.out.println();

        System.out.println("Customer Metrics");
        System.out.println("  Average Wait Time (minutes): " + f2(avgWait));
        System.out.println("  Maximum Wait Time (minutes): " + f2(maxWait));
        System.out.println("  Percent Exceeding Wait Time Limit: " + f2(percentWaitAboveLimit) + "%");
        System.out.println("  Percent With Zero Wait: " + f2(percentZeroWait) + "%");
        System.out.println("  Total Customers Arrived: " + totalArrivals);
        System.out.println("  Total Customers Served: " + totalServed);
        System.out.println();

        System.out.println("Queue Metrics");
        System.out.println("  Average Queue Length: " + f2(avgQueueLength));
        System.out.println("  Maximum Queue Length: " + maxQueueLength);
        System.out.println("  Time Queue Was Non Empty (minutes): " + f2(timeQueueNonEmpty));
        System.out.println("  Time Queue Was Non Empty (percent): " + f2(timeQueueNonEmptyPercent) + "%");
        System.out.println("  Average Customers In System: " + f2(avgCustomersInSystem));
        System.out.println("  Average Time In System (minutes): " + f2(avgSystemTime));
        System.out.println();

        System.out.println("Server Metrics");
        System.out.println("  Overall Utilization (percent): " + f2(overallUtilization) + "%");
        for (int i = 0; i < serverUtilization.length; i++) {
            System.out.println("  Server " + (i + 1) + " Utilization (percent): " + f2(serverUtilization[i]) + "%");
        }
        System.out.println();

        System.out.println("System Metrics");
        System.out.println("  Traffic Intensity: " + f2(trafficIntensity));
        System.out.println("  System Stability: " + systemStability);
        System.out.println("  Little's Law Observed L: " + f2(avgCustomersInSystem));
        System.out.println("  Little's Law Predicted Lambda W: " + f2(predictedL));
        System.out.println();

        writeSummaryCsv(
                avgWait,
                avgSystemTime,
                avgQueueLength,
                avgCustomersInSystem,
                timeQueueNonEmptyPercent,
                overallUtilization,
                serverUtilization,
                percentWaitAboveLimit,
                percentZeroWait,
                trafficIntensity,
                systemStability,
                predictedL
        );

        if (timeSeriesWriter != null) {
            timeSeriesWriter.close();
        }
        if (summaryWriter != null) {
            summaryWriter.close();
        }

        System.out.println("CSV files created:");
        System.out.println("  run_" + runId + "_timeseries.csv");
        System.out.println("  run_" + runId + "_summarymetrics.csv");
        System.out.println("==================================================");
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("==================================================");
        System.out.println("Discrete Event Restaurant Simulation");
        System.out.println("==================================================");
        System.out.println();

        System.out.print("Enter run ID for CSV files: ");
        String runId = sc.nextLine();

        System.out.print("Enter number of servers: ");
        int servers = Integer.parseInt(sc.nextLine());

        System.out.print("Enter simulation duration (hours): ");
        double hours = Double.parseDouble(sc.nextLine());

        System.out.print("Enter arrival rate (customers per hour): ");
        double lambda = Double.parseDouble(sc.nextLine());

        System.out.print("Enter average service time (minutes): ");
        double meanService = Double.parseDouble(sc.nextLine());

        System.out.print("Enter wait time limit (minutes): ");
        double waitTimeLimit = Double.parseDouble(sc.nextLine());

        Simulation simulation = new Simulation(
                servers,
                hours,
                lambda,
                meanService,
                runId,
                waitTimeLimit
        );

        System.out.println();
        System.out.println("Running simulation...");
        simulation.run();
        simulation.printResults();
    }
}