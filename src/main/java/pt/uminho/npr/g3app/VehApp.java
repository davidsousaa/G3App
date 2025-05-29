package pt.uminho.npr.g3app;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.MutableGeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

public class VehApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {

    private final long MsgDelay = 200 * TIME.MILLI_SECOND;
    private final int Power = 50;
    private final double Distance = 140.0;

    private int setVal;

    private double vehHeading;
    private double vehSpeed;
    private int vehLane;

    private Map<String, VehInfoMsg> neighbors = new ConcurrentHashMap<>();
    private Map<String, Long> neighborsTimestamps = new ConcurrentHashMap<>();
    private Map<String, Long> neighborsRSU = new ConcurrentHashMap<>();
    private final Long NeighborTimeout = 500 * TIME.MILLI_SECOND;
    private final Long RSUTimeout = 800 * TIME.MILLI_SECOND;
    private GeoPoint rsuPos = new MutableGeoPoint(0.0, 0.0);

    @Override
    public void onShutdown() {
        //writeNeighborsToCsv();
        getLog().infoSimTime(this, "onShutdown");
        getOs().getAdHocModule().disable();
    }

    @Override
    public void onStartup() {
        getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                .addRadio()
                .channel(AdHocChannel.CCH)
                .power(Power)
                .distance(Distance)
                .create());

        getLog().infoSimTime(this, "onStartup: Set up");
        setVal = 0;
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + MsgDelay, this);
    }

    @Override
    public void processEvent(Event arg0) throws Exception {
        getLog().infoSimTime(this, "processEvent");
        if (setVal == 1) {
            sendVehInfoMsg();
        }
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + MsgDelay, this);
    }

    @Override
    public void onMessageReceived(@Nonnull ReceivedV2xMessage receivedMessage) {
        //getLog().infoSimTime(this, "onMessageReceived");
        //TODO: process received message
        V2xMessage msg = receivedMessage.getMessage();

        if (msg instanceof VehInfoMsg vehInfoMsg) {

            if (vehInfoMsg.getSenderName().equals(getOs().getId())) {
                return;
            }
            updateNeighbors(vehInfoMsg);

        } else if (msg instanceof RSUHello rsuHello) {
            String rsuId = rsuHello.getSenderName();
            long currentTime = getOs().getSimulationTime();

            neighborsRSU.put(rsuId, currentTime);
            rsuPos = rsuHello.getSenderPos();

            if (getOs().getId().equals("veh_5")) {
                getLog().infoSimTime(this, "Received RSUHello from " + rsuId + " at position " + rsuPos);
                getLog().infoSimTime(this, "RSU connected: " + neighborsRSU.keySet().toString());
            }
        }

        // log neighbors table to output.csv (can be done in onShutdown())
    }

    public void updateNeighbors(VehInfoMsg vehInfoMsg) {
        String id = vehInfoMsg.getSenderName();
        long currentTime = getOs().getSimulationTime();

        neighbors.put(id, vehInfoMsg);
        neighborsTimestamps.put(id, currentTime);

        removeOldNeighbors();
    }

    // Em vez que fazer os loops, podemos remover apenas quando precisamos de usar os neighbors e, se o timestamp for maior que o timeout, ignore e remove
    public void removeOldNeighbors() {
        long currentTime = getOs().getSimulationTime();
        Iterator<Map.Entry<String, Long>> iterator = neighborsTimestamps.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            long timestamp = entry.getValue();

            if (currentTime - timestamp > NeighborTimeout) {
                String id = entry.getKey();
                iterator.remove();
                neighbors.remove(id);
            }
        }
    }

    private void writeNeighborsToCsv() {
        String filename = "neighbors_" + getOs().getId() + ".csv"; // One file per RSU/vehicle
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("NeighborID,LastSeenTime");
            for (Map.Entry<String, Long> entry : neighborsTimestamps.entrySet()) {
                writer.println(entry.getKey() + "," + entry.getValue());
            }
            getLog().infoSimTime(this, "Neighbors table written to: " + filename);
        } catch (IOException e) {
            getLog().warnSimTime(this, "Error writing neighbors CSV: " + e.getMessage());
        }
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission arg0) {
        getLog().infoSimTime(this, "onMessageTransmitted");
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        getLog().infoSimTime(this, "onVehicleUpdated");
        if (setVal == 0) {
            setVal = 1;
        }
        this.vehHeading = updatedVehicleData.getHeading().doubleValue();
        this.vehSpeed = updatedVehicleData.getSpeed();
        this.vehLane = updatedVehicleData.getRoadPosition().getLaneIndex();
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement arg0) {
        getLog().infoSimTime(this, "onAcknowledgementReceived");

    }

    @Override
    public void onCamBuilding(CamBuilder arg0) {
        getLog().infoSimTime(this, "onCamBuilding");
    }

    private void removeOldRSUs() {
        long currentTime = getOs().getSimulationTime();
        Iterator<Map.Entry<String, Long>> iterator = neighborsRSU.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            long timestamp = entry.getValue();

            if (currentTime - timestamp > RSUTimeout && neighborsRSU.size() > 1) {
                iterator.remove();
            }
        }
    }

    private String getRSU() {
        removeOldRSUs();

        if (!neighborsRSU.isEmpty()) {
            for (String rsuId : neighborsRSU.keySet()) {
                return rsuId;
            }
        }

        return null;
    }

    private boolean isRSUConnected() {
        removeOldRSUs();
        long currentTime = getOs().getSimulationTime();
        for (Map.Entry<String, Long> entry : neighborsRSU.entrySet()) {
            long timestamp = entry.getValue();

            if (currentTime - timestamp > RSUTimeout) {
                return false;
            }
        }
        return true;
    }

    private void sendVehInfoMsg() {
        MessageRouting routing = getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoBroadCast();
        long time = getOs().getSimulationTime();
        String rsu = this.getRSU();
        if (rsu == null) {
            rsu = "rsu_0";
        }
        boolean isRsuConnected = isRSUConnected();
        VehInfoMsg message = new VehInfoMsg(routing, time, getOs().getId(), getOs().getPosition(), this.vehHeading, this.vehSpeed, this.vehLane, rsu, isRsuConnected);
        getOs().getAdHocModule().sendV2xMessage(message);
        getLog().infoSimTime(this, "Sent VehInfoMsg: " + message.toString());
    }
}
