public class Event {
    public final double time;         // minutes
    public final EventType type;
    public final int customerId;
    public final int serverId;         // only used for DEPARTURE

    public Event(double time, EventType type, int customerId, int serverId) {
        this.time = time;
        this.type = type;
        this.customerId = customerId;
        this.serverId = serverId;
    }
}