package pt.uminho.npr.g3app;

import javax.annotation.Nonnull;

import org.eclipse.mosaic.rti.DATA;
import org.eclipse.mosaic.rti.TIME;

import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.regex.*;

import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.objects.traffic.SumoTraciResult;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.MosaicApplication;
import org.eclipse.mosaic.interactions.application.ApplicationInteraction;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModuleConfiguration;

import java.util.ArrayList;

public class FogNodeApp extends AbstractApplication<ServerOperatingSystem>
        implements CommunicationApplication, MosaicApplication {

    private ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> laneOccupation = new ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>();
    private boolean changedRoute = false;

    @Override
    public void onStartup() {
        getOs().getCellModule().enable(new CellModuleConfiguration()
                .maxDownlinkBitrate(50 * DATA.MEGABIT)
                .maxUplinkBitrate(50 * DATA.MEGABIT));

        getLog().infoSimTime(this, "Fog Node started and ready to receive RSU interactions.");
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Fog Node shutting down.");
    }

    @Override
    public void processEvent(Event event) {
        cleanupLaneInfo();
    }

    public void cleanupLaneInfo() {
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + 1000 * TIME.MILLI_SECOND, this);
        long currentTime = getOs().getSimulationTime();

        for (ConcurrentHashMap.Entry<String, ConcurrentHashMap<String, Long>> entry : laneOccupation.entrySet()) {
            ConcurrentHashMap<String, Long> vehiclesInLane = entry.getValue();

            for (ConcurrentHashMap.Entry<String, Long> vehicleEntry : vehiclesInLane.entrySet()) {
                long timestamp = vehicleEntry.getValue();
                if ((currentTime - timestamp) > 1000) {
                    vehiclesInLane.remove(vehicleEntry.getKey());
                }
            }
        }
    }

    @Override
    public void onMessageReceived(@Nonnull ReceivedV2xMessage receivedMessage) {
        // Not used in this interaction-based example
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission transmission) {
        // Not used in this interaction-based example
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
        // Not used in this example
    }

    @Override
    public void onAcknowledgementReceived(
            org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement ack) {
        // Not used in this example
    }

    @Override
    public void onInteractionReceived(ApplicationInteraction interaction) {
        if (interaction instanceof RsuFogInteraction) {
            RsuFogInteraction rsuMsg = (RsuFogInteraction) interaction;
            // getLog().infoSimTime(this, "Received msg from " + rsuMsg.getSourceUnitId() +
            // ": " + rsuMsg.getContent());

            if (rsuMsg.getUnitId().equals(getOs().getId())) {
                // send a responde back
                RsuFogInteraction response = new RsuFogInteraction(getOs().getSimulationTime(), rsuMsg.getSenderId(),
                        "Received by server_0", getOs().getId());
                getOs().sendInteractionToRti(response);
                processMessage(rsuMsg);
            } else {
                getLog().infoSimTime(this,
                        "Fog Node received RsuFogInteraction not targeted to this node: " + rsuMsg.getContent());
            }
        } else {
            getLog().infoSimTime(this, "Fog Node received unknown interaction type: " + interaction.toString());
        }
    }

    private void processMessage(RsuFogInteraction message) {
        String content = message.getContent();
        //String messageType = content.split("\\{")[0].strip();
        Pattern pattern = Pattern.compile("(\\w+Msg)\\{");
        Matcher matcher = pattern.matcher(content);
        String messageType = null;

        if (matcher.find()) {
            messageType = matcher.group(1);
        }

        getLog().infoSimTime(this, "Processing message of type: " + messageType);
        switch (messageType) {
            case "VehInfoMsg":
                processStatusMessage(content);
                break;
            case "WarningMsg":
                if (!changedRoute) {
                    processWarningMessage(content, message.getSenderId());
                    getLog().infoSimTime(this, "Processing WarningMsg: " + content);
                } else {
                    getLog().warnSimTime(this, "WarningMessage already processed, ignoring: " + content);
                }
                break;
            default:
                getLog().warnSimTime(this, "Unknown message type received: " + messageType + " with content: " + content);
                break;
        }
    }

    private void processStatusMessage(String message) {
        String laneId = message.split("senderLaneId=")[1].split("\\}")[0].strip();
        String vehicleId = message.split("senderName=")[1].split(",")[0].strip();
        String timestampString = message.split("timeStamp=")[1].split(",")[0].strip();
        long timestamp = Long.parseLong(timestampString);

        // Update timestamp value of the vehicle
        for (ConcurrentHashMap.Entry<String, ConcurrentHashMap<String, Long>> entry : laneOccupation.entrySet()) {
            String currentLaneId = entry.getKey();
            ConcurrentHashMap<String, Long> vehiclesInLane = entry.getValue();

            if (vehiclesInLane.containsKey(vehicleId)) {
                if (currentLaneId.equals(laneId)) {
                    vehiclesInLane.put(vehicleId, timestamp);
                    return; // No need to check if it is in another lane, it would be caught before when
                    // added to this new lane
                } else {
                    // Vehicle registered in a different lane
                    vehiclesInLane.remove(vehicleId);
                    break;
                }
            }
        }

        // If the vehicle was not in the correct lane, add it
        laneOccupation
                .computeIfAbsent(laneId, k -> new ConcurrentHashMap<>())
                .put(vehicleId, timestamp);
    }

    private void processWarningMessage(String message, String senderId) {

        ArrayList<String> assignedRSUs = getAssignedRSUs();
        if (assignedRSUs.isEmpty()) {
            getLog().warnSimTime(this, "No RSUs assigned to this Fog Node.");
            return;
        }

        for (String rsuId : assignedRSUs) {
            RsuFogInteraction interaction = new RsuFogInteraction(getOs().getSimulationTime(), rsuId, "Reroute", getOs().getId());
            getOs().sendInteractionToRti(interaction);
            getLog().infoSimTime(this, "Sent Reroute interaction to RSU: " + rsuId);
        }
        this.changedRoute = true;
    }

    private ArrayList<String> getAssignedRSUs() {
        ArrayList<String> assignedRSUs = new ArrayList<>();
        if (getOs().getId().equals("server_0")) {
            assignedRSUs.add("rsu_0");
            assignedRSUs.add("rsu_1");
            assignedRSUs.add("rsu_2");
        } else {
            assignedRSUs.add("rsu_3");
            assignedRSUs.add("rsu_4");
            assignedRSUs.add("rsu_5");
        }
        return assignedRSUs;
    }

    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
        // Not used in this example
    }

}
