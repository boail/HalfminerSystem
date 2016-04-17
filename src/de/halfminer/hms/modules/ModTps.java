package de.halfminer.hms.modules;

import de.halfminer.hms.HalfminerSystem;
import de.halfminer.hms.util.Language;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedList;

/**
 * - Calculates ticks per second
 * - Notifies staff when servers Tps is too low
 */
@SuppressWarnings("unused")
public class ModTps extends HalfminerModule implements Listener {

    private final static HalfminerSystem hms = HalfminerSystem.getInstance();
    private final LinkedList<Double> tpsHistory = new LinkedList<>();
    private BukkitTask task;
    private double lastAverageTps;
    private long lastTaskTimestamp;

    // Config
    private int ticksBetweenUpdate;
    private int historySize;
    private double alertStaff;

    /**
     * Returns average TPS over last 10 polled values
     *
     * @return Double, average in tpsHistory
     */
    public double getTps() {
        return lastAverageTps;
    }

    @Override
    public void loadConfig() {

        ticksBetweenUpdate = config.getInt("tps.ticksBetweenUpdate", 100);
        historySize = config.getInt("tps.historySize", 6);
        alertStaff = config.getDouble("tps.alertThreshold", 17.0d);

        if (task != null) task.cancel();

        tpsHistory.clear();
        tpsHistory.add(20.0);
        lastAverageTps = 20.0;
        lastTaskTimestamp = System.currentTimeMillis();
        task = scheduler.runTaskTimer(hms, new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long lastUpdate = now - lastTaskTimestamp; //time in milliseconds since last check
                lastTaskTimestamp = now;

                double currentTps = ticksBetweenUpdate * 1000.0 / lastUpdate;

                if (currentTps > 21.0d) return; //disregard peaks due to fluctuations

                if (tpsHistory.size() >= historySize) tpsHistory.removeFirst();
                tpsHistory.add(currentTps);

                // Get average value
                lastAverageTps = 0.0;
                for (Double val : tpsHistory) lastAverageTps += val;
                lastAverageTps /= tpsHistory.size();
                lastAverageTps = Math.round(lastAverageTps * 100.0) / 100.0; //round value to two decimals

                // send message if server is unstable
                if (lastAverageTps < alertStaff && tpsHistory.size() == historySize)
                    server.broadcast(Language.getMessagePlaceholders("modTpsServerUnstable",
                            true, "%PREFIX%", "Lag", "%TPS%", String.valueOf(lastAverageTps)), "hms.lag.notify");
            }
        }, ticksBetweenUpdate, ticksBetweenUpdate);
    }
}
