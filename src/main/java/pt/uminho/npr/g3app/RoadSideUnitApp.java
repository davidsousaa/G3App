package pt.uminho.npr.g3app;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nonnull;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.MosaicApplication;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.interactions.application.ApplicationInteraction;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.traffic.SumoTraciResult;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.DATA;
import org.eclipse.mosaic.rti.TIME;

public class RoadSideUnitApp extends AbstractApplication<RoadSideUnitOperatingSystem>
        implements CommunicationApplication, MosaicApplication {

    private final Map<String, VehInfoMsg> neighbors = new HashMap<>();
    private final Map<String, Long> neighborsTimestamps = new HashMap<>();
    private static final long NeighborTimeout = 500 * TIME.MILLI_SECOND;
    private boolean changeRoute = false;
    private boolean warningReceived = false;

    @Override
    public void onStartup() {
        getOs().getAdHocModule().enable(
                new AdHocModuleConfiguration()
                        .addRadio()
                        .channel(AdHocChannel.CCH)
                        .power(50)
                        .distance(140.0)
                        .create());

        getOs().getCellModule().enable(new CellModuleConfiguration()
                .maxDownlinkBitrate(50 * DATA.MEGABIT)
                .maxUplinkBitrate(50 * DATA.MEGABIT));

        getLog().infoSimTime(this, "RSU App started!");
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + 500 * TIME.MILLI_SECOND, this);
    }

    public String getFogNode() {
        float idValue = getIntId();

        if (idValue < 0 || idValue > 5) {
            return "server_0"; // fallback
        }

        if (idValue < 3) {
            return "server_0";
        } else {
            return "server_1";
        }
    }

    // int value
    private int getIntId() {
        String id = getOs().getId();
        String[] parts = id.split("_");
        if (parts.length > 1) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                getLog().warnSimTime(this, "Invalid ID format: " + id);
            }
        }
        return -1; // default value if parsing fails
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "RSU App shutting down.");
        //saveNeighborsToFile();
    }

    private void saveNeighborsToFile() {
        String id = getOs().getId();
        String fileName = "logs/neighbors_" + id + ".csv";

        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            writer.println("NeighborID,LastSeenTime");
            for (Map.Entry<String, Long> entry : neighborsTimestamps.entrySet()) {
                writer.println(entry.getKey() + "," + entry.getValue());
            }
        } catch (IOException e) {
            getLog().warnSimTime(this, "Failed to write neighbors file: " + e.getMessage());
        }
    }

    @Override
    public void onMessageReceived(@Nonnull ReceivedV2xMessage receivedMessage) {
        V2xMessage msg = receivedMessage.getMessage();

        switch (msg) {
            case VehInfoMsg vehMsg -> {
                if (!vehMsg.getSenderName().equals(getOs().getId())) {
                    updateNeighbors(vehMsg);
                }

                String sender = vehMsg.getSenderName();
                getLog().infoSimTime(this, "RSU: Received VehInfoMsg from " + sender + " sending it to Fog Node.");
                RsuFogInteraction interaction = new RsuFogInteraction(
                        getOs().getSimulationTime(), getFogNode(),
                        "Forwarded VehInfoMsg from " + sender + " | " + msg.toString(),
                        getOs().getId()
                );
                getOs().sendInteractionToRti(interaction);
            }
            case WarningMsg warningMsg -> {
                if (!warningMsg.getSenderName().equals(getOs().getId()) && !warningReceived) {
                    warningReceived = true;
                    getLog().infoSimTime(this, "RSU: Received WarningMsg from " + warningMsg.getSenderName() + ", sending it to Fog Node.");
                    RsuFogInteraction interaction = new RsuFogInteraction(
                            getOs().getSimulationTime(), getFogNode(),
                            "Forwarded WarningMsg from " + warningMsg.getSenderName() + " | " + msg.toString(),
                            getOs().getId()
                    );
                    getOs().sendInteractionToRti(interaction);
                }
            }
            default ->
                getLog().warnSimTime(this, "RSU: Received unknown message type: " + msg.getClass().getSimpleName());
        }
    }

    public void updateNeighbors(VehInfoMsg vehInfoMsg) {
        String id = vehInfoMsg.getSenderName();
        long currentTime = getOs().getSimulationTime();

        neighbors.put(id, vehInfoMsg);
        neighborsTimestamps.put(id, currentTime);

        removeOldNeighbors();
    }

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

    private void sendHelloMessage() {
        long currentTime = getOs().getSimulationTime();
        GeoPoint pos = getOs().getPosition();

        MessageRouting routing = getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoBroadCast();

        RSUHello helloMsg = new RSUHello(
                routing,
                getOs().getId(),
                pos,
                currentTime
        );

        getOs().getAdHocModule().sendV2xMessage(helloMsg);
        getLog().infoSimTime(this, "Sent RSUHello message: " + helloMsg.toString());
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission arg0) {
        getLog().infoSimTime(this, "RSU: Message transmitted.");
    }

    @Override
    public void onCamBuilding(CamBuilder arg0) {
        getLog().infoSimTime(this, "RSU: onCamBuilding");
    }

    @Override
    public void processEvent(Event event) {
        sendHelloMessage();

        if (this.changeRoute) {
            sendRerouteMessage();
        }

        getOs().getEventManager().addEvent(getOs().getSimulationTime() + 500 * TIME.MILLI_SECOND, this);
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement arg0) {
        getLog().infoSimTime(this, "RSU: ACK received.");
    }

    public void sendRerouteMessage() {
        String newRoute = "r_1";
        MessageRouting routing = getOs().getAdHocModule().createMessageRouting()
                .viaChannel(AdHocChannel.CCH)
                .topoBroadCast();

        RerouteMsg rerouteMsg = new RerouteMsg(
                routing,
                getOs().getId(),
                getOs().getSimulationTime(),
                newRoute
        );

        getOs().getAdHocModule().sendV2xMessage(rerouteMsg);
        getLog().infoSimTime(this, "Sent RerouteMsg: " + rerouteMsg.toString());
    }

    @Override
    public void onInteractionReceived(ApplicationInteraction interaction) {
        getLog().infoSimTime(this, "RSU: Received interaction: " + interaction.toString());
        if (interaction instanceof RsuFogInteraction rsuMsg) {
            if (rsuMsg.getContent().equals("Reroute")) {

                getLog().infoSimTime(this, "RSU: Received RsuFogInteraction with content: " + rsuMsg.getContent());
                this.changeRoute = true;

            } else {
                getLog().infoSimTime(this, "RSU: Received RsuFogInteraction: " + rsuMsg.getContent());
            }
        } else {
            getLog().infoSimTime(this, "RSU: Received unknown interaction type: " + interaction.toString());
        }
    }

    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
        // Not used in this example
    }
}
