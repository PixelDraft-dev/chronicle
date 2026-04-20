package com.chronicle.database;

import com.chronicle.buffer.ActionPacket;

import java.sql.Timestamp;
import java.util.*;


public final class ReportRecord {


    private long      id;
    private UUID      accusedUuid;
    private String    accusedName;
    private UUID      reporterUuid;
    private String    reporterName;
    private String    reason;
    private String    world;
    private double    posX, posY, posZ;
    private String    status;         
    private Timestamp createdAt;
    private Timestamp reviewedAt;
    private UUID      reviewedBy;


    private byte[]            tdfBlob;

    private List<ActionPacket> tdfPackets;


    private Map<int[], String> sceneBlocks;



    public ReportRecord() { }


    public static ReportRecord create(
            UUID accusedUuid,   String accusedName,
            UUID reporterUuid,  String reporterName,
            String reason,      String world,
            double x, double y, double z,
            byte[] tdfBlob
    ) {
        ReportRecord r = new ReportRecord();
        r.accusedUuid  = accusedUuid;
        r.accusedName  = accusedName;
        r.reporterUuid = reporterUuid;
        r.reporterName = reporterName;
        r.reason       = reason;
        r.world        = world;
        r.posX = x; r.posY = y; r.posZ = z;
        r.tdfBlob      = tdfBlob;
        r.status       = "OPEN";
        return r;
    }



    public long      getId()            { return id; }
    public void      setId(long v)      { this.id = v; }

    public UUID      getAccusedUuid()       { return accusedUuid; }
    public void      setAccusedUuid(UUID v) { this.accusedUuid = v; }

    public String    getAccusedName()       { return accusedName; }
    public void      setAccusedName(String v){ this.accusedName = v; }

    public UUID      getReporterUuid()        { return reporterUuid; }
    public void      setReporterUuid(UUID v)  { this.reporterUuid = v; }

    public String    getReporterName()        { return reporterName; }
    public void      setReporterName(String v){ this.reporterName = v; }

    public String    getReason()        { return reason; }
    public void      setReason(String v){ this.reason = v; }

    public String    getWorld()         { return world; }
    public void      setWorld(String v) { this.world = v; }

    public double    getPosX()          { return posX; }
    public void      setPosX(double v)  { this.posX = v; }

    public double    getPosY()          { return posY; }
    public void      setPosY(double v)  { this.posY = v; }

    public double    getPosZ()          { return posZ; }
    public void      setPosZ(double v)  { this.posZ = v; }

    public String    getStatus()        { return status; }
    public void      setStatus(String v){ this.status = v; }

    public Timestamp getCreatedAt()          { return createdAt; }
    public void      setCreatedAt(Timestamp v){ this.createdAt = v; }

    public Timestamp getReviewedAt()          { return reviewedAt; }
    public void      setReviewedAt(Timestamp v){ this.reviewedAt = v; }

    public UUID      getReviewedBy()        { return reviewedBy; }
    public void      setReviewedBy(UUID v)  { this.reviewedBy = v; }

    public byte[]              getTdfBlob()          { return tdfBlob; }
    public void                setTdfBlob(byte[] v)  { this.tdfBlob = v; }

    public List<ActionPacket>  getTdfPackets()           { return tdfPackets; }
    public void                setTdfPackets(List<ActionPacket> v){ this.tdfPackets = v; }

    public Map<int[], String>  getSceneBlocks()          { return sceneBlocks; }
    public void                setSceneBlocks(Map<int[], String> v){ this.sceneBlocks = v; }



    public boolean hasTdfData()  { return tdfPackets != null && !tdfPackets.isEmpty(); }
    public boolean isOpen()      { return "OPEN".equals(status); }
    public boolean isReviewing() { return "REVIEWING".equals(status); }
    public boolean isClosed()    { return "CLOSED".equals(status); }

    @Override
    public String toString() {
        return "ReportRecord{id=%d, accused=%s, status=%s, frames=%d}"
                .formatted(id, accusedName, status,
                        tdfPackets != null ? tdfPackets.size() : -1);
    }
}
