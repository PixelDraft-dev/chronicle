package com.chronicle.replay;

import com.chronicle.buffer.ActionPacket;
import com.chronicle.database.ReportRecord;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;


public final class ReplaySession {

    public enum State { PLAYING, PAUSED, FINISHED }


    private final UUID         adminUuid;
    private final ReportRecord report;


    private final List<ActionPacket> packets;
    private       int                frameIndex  = 0;
    private       double             speed       = 1.0;    
    private       State              state       = State.PLAYING;



    private final Location savedLocation;
    private final GameMode savedGameMode;
    private final boolean  savedFlyAllowed;
    private final boolean  savedFlying;

    private final GhostPlayer ghost;


    private final long startedAt = System.currentTimeMillis();
    private       long lastFrameAt = System.currentTimeMillis();

    public ReplaySession(Player admin, ReportRecord report,
                         List<ActionPacket> packets, GhostPlayer ghost) {
        this.adminUuid    = admin.getUniqueId();
        this.report       = report;
        this.packets      = List.copyOf(packets);
        this.ghost        = ghost;

  
        this.savedLocation  = admin.getLocation().clone();
        this.savedGameMode  = admin.getGameMode();
        this.savedFlyAllowed = admin.getAllowFlight();
        this.savedFlying     = admin.isFlying();
    }


    public ActionPacket nextFrame() {
        if (frameIndex >= packets.size()) {
            state = State.FINISHED;
            return null;
        }
        return packets.get(frameIndex++);
    }


    public ActionPacket currentFrame() {
        if (frameIndex >= packets.size()) return null;
        return packets.get(frameIndex);
    }

    public boolean hasNextFrame() { return frameIndex < packets.size(); }


    public void rewind() { frameIndex = 0; state = State.PLAYING; }

    public void seekTo(int index) {
        this.frameIndex = Math.max(0, Math.min(index, packets.size() - 1));
    }


    public void setSpeed(double speed) {
        if (speed <= 0) throw new IllegalArgumentException("Speed must be > 0");
        this.speed = speed;
    }


    public long schedulerDelayTicks() {
        return Math.max(1L, Math.round(1.0 / speed));
    }


    public int framesPerTick() {
        return speed > 1.0 ? (int) Math.round(speed) : 1;
    }


    public void pause()  { state = State.PAUSED;  }
    public void resume() { state = State.PLAYING; }

    public State   getState()      { return state; }
    public boolean isPlaying()     { return state == State.PLAYING;  }
    public boolean isPaused()      { return state == State.PAUSED;   }
    public boolean isFinished()    { return state == State.FINISHED; }



    public UUID            getAdminUuid()     { return adminUuid; }
    public ReportRecord    getReport()        { return report; }
    public List<ActionPacket> getPackets()    { return packets; }
    public int             getFrameIndex()    { return frameIndex; }
    public int             getTotalFrames()   { return packets.size(); }
    public double          getSpeed()         { return speed; }
    public GhostPlayer     getGhost()         { return ghost; }
    public long            getStartedAt()     { return startedAt; }



    public Location getSavedLocation()  { return savedLocation.clone(); }
    public GameMode getSavedGameMode()  { return savedGameMode; }
    public boolean  isSavedFlyAllowed() { return savedFlyAllowed; }
    public boolean  isSavedFlying()     { return savedFlying; }

    public int progressPercent() {
        if (packets.isEmpty()) return 100;
        return (int)((frameIndex / (double) packets.size()) * 100);
    }
}
