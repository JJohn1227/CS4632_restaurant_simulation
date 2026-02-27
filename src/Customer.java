public class Customer {
public final int id;
public final double arrivalTime;
public Double serviceStartTime; // set when service begins
public Double departureTime;// set when service ends

 public Customer(int id, double arrivalTime) {
    this.id = id;
    this.arrivalTime = arrivalTime;
}

// This calculates how long customer waited in queue
 public double getWaitTime() {
     if (serviceStartTime == null) return 0.0;
    return serviceStartTime - arrivalTime;
}

// This calculates total time in system
 public double getTimeInSystem() {
    if (departureTime == null) return 0.0;
    return departureTime - arrivalTime;
}
}