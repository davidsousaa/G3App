package pt.uminho.npr.g3app;

import javax.annotation.Nonnull;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.RoadSideUnitOperatingSystem;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.lib.enums.AdHocChannel; 
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.rti.DATA;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModuleConfiguration;
import org.eclipse.mosaic.interactions.application.ApplicationInteraction;
import org.eclipse.mosaic.fed.application.app.api.MosaicApplication;
import org.eclipse.mosaic.lib.objects.traffic.SumoTraciResult;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import org.eclipse.mosaic.rti.TIME;


public class RoadSideUnitApp extends AbstractApplication<RoadSideUnitOperatingSystem>
        implements CommunicationApplication, MosaicApplication {

    private final Map<String, VehInfoMsg> neighbors = new HashMap<>();
    private final Map<String, Long> neighborsTimestamps = new HashMap<>();
    private static final long NeighborTimeout = 500 * TIME.MILLI_SECOND;
            

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
    }

    public String getFogNode() {
        float idValue = getIntId();
    
        if (idValue < 0 || idValue > 43) {
            return "server_0"; // fallback
        }
    
        if (idValue < 9) {
            return "server_0";
        } else if (idValue < 18) {
            return "server_1";
        } else if (idValue < 27) {
            return "server_2";
        } else if (idValue < 36) {
            return "server_3";
        } else {
            return "server_4";
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
    
        if (msg instanceof VehInfoMsg) {
            VehInfoMsg vehMsg = (VehInfoMsg) msg;
            if (!vehMsg.getSenderName().equals(getOs().getId())) {
                updateNeighbors(vehMsg);
            }
        
            String sender = vehMsg.getSenderName();
            getLog().infoSimTime(this, "RSU: Received VehInfoMsg from " + sender  + " sending it to Fog Node.");
            RsuFogInteraction interaction = new RsuFogInteraction(
                getOs().getSimulationTime(), getFogNode(),
                "Forwarded VehInfoMsg from " + sender + " | " + msg.toString(),
                getOs().getId()
            );
            getOs().sendInteractionToRti(interaction);
        }
    }

    public void updateNeighbors(VehInfoMsg vehInfoMsg){
        String id = vehInfoMsg.getSenderName();
        long currentTime = getOs().getSimulationTime();
    
        neighbors.put(id, vehInfoMsg);
        neighborsTimestamps.put(id, currentTime);
    
        removeOldNeighbors();
    }

    public void removeOldNeighbors(){
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
        // No periodic logic for now, but required to implement
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement arg0) {
        getLog().infoSimTime(this, "RSU: ACK received.");
    }

    @Override
    public void onInteractionReceived(ApplicationInteraction interaction) {
        getLog().infoSimTime(this, "RSU: Received interaction: " + interaction.toString());
        if (interaction instanceof RsuFogInteraction) {
            RsuFogInteraction rsuMsg = (RsuFogInteraction) interaction;
            getLog().infoSimTime(this, "RSU: Received RsuFogInteraction: " + rsuMsg.getContent());
        } else {
            getLog().infoSimTime(this, "RSU: Received unknown interaction type: " + interaction.toString());
        }
    }

    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
        // Not used in this example
    }
}
