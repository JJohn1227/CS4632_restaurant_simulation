public class Server {
public int id;
public boolean busy;
public int currentCustomerId;

public double busyTime;// total time busy
            public double busyUntil;// time server will be free

    public Server(int id) {
    this.id = id;
    this.busy = false;
    this.currentCustomerId = -1;
    this.busyTime = 0.0;
    this.busyUntil = 0.0;
}

// starts service for a customer
        public void startService(int customerId, double startTime, double serviceDuration) {
    busy = true;
    currentCustomerId = customerId;
    busyUntil = startTime + serviceDuration;
    busyTime += serviceDuration;
}

// ends service and frees server
        public void endService() {
    busy = false;
    currentCustomerId = -1;
}
}