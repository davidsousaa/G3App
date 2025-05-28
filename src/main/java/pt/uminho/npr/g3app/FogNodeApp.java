package pt.uminho.npr.g3app;

import java.util.List;
import javax.annotation.Nonnull;
import org.eclipse.mosaic.rti.DATA;
import org.apache.commons.lang3.tuple.Pair;
import java.util.concurrent.ConcurrentHashMap;
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

public class FogNodeApp extends AbstractApplication<ServerOperatingSystem>
        implements CommunicationApplication, MosaicApplication {

    private ConcurrentHashMap<String, List<Pair<String, Long>>> laneOccupation = new ConcurrentHashMap<String, List<Pair<String, Long>>>();

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
        // No scheduled events in this example
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
                processMessage(response);
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
        String messageType = content.split("\\{")[0];
        switch (messageType) {
            case "VehInfoMessage":
                this.processStatusMessage(content);
                break;
            case "WarningMessage":
                this.processWarningMessage(content);
                break;
            default:
                break;
        }
    }

    /*
     * public String toString() {
     * return "VehInfoMessage{" +
     * "timeStamp=" + timeStamp +
     * ", senderName=" + senderName +
     * ", senderPosition=" + senderPos +
     * ", senderHeading=" + senderHeading +
     * ", senderSpeed=" + senderSpeed +
     * ", senderLaneId=" + senderLaneId + '}';
     * }
     */

    private void processStatusMessage(String message) {
        // TODO: Check if it need more fields
        String laneId = message.split("senderLaneId=")[1].split("\\}")[0];
        String vehicleId = message.split("senderName=")[1].split(",")[0];
        String timestampString = message.split("timeStamp=")[1].split(",")[0];
        long timestamp = Long.parseLong(timestampString);
        Pair<String, Long> vehichleTimestamp = Pair.of(vehicleId, timestamp);

        List<Pair<String, Long>> laneInfo = this.laneOccupation.get(laneId);
        for (Pair<String, Long> pair : laneInfo) {
            if (pair.getLeft().equals(vehicleId)) {
                laneInfo.remove(pair);
                break;
            }
        }

        laneInfo.add(vehichleTimestamp);
        this.laneOccupation.put(laneId, laneInfo);
        // TODO:
        // Outside this, método para limpar se timestamp (pair.getLeft()) difference
        // maior que 0.5s ou 1s
    }

    private void processWarningMessage(String message) {

    }

    /*
     * private List<String> getAssignedRsus() {
     * String fogId = getOs().getId();
     * 
     * int fogIndex;
     * try {
     * fogIndex = Integer.parseInt(fogId.split("_")[1]);
     * } catch (Exception e) {
     * getLog().infoSimTime(this, "Invalid fog ID format: " + fogId);
     * return Collections.emptyList(); // fallback
     * }
     * 
     * List<String> rsus = new ArrayList<>();
     * int start = fogIndex * 9;
     * int end = (fogIndex == 4) ? 44 : start + 9;
     * 
     * for (int i = start; i < end; i++) {
     * rsus.add("rsu_" + i);
     * }
     * 
     * return rsus;
     * }
     */

    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
        // Not used in this example
    }

}
