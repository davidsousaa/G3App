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
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModuleConfiguration;
import org.eclipse.mosaic.lib.objects.addressing.CellMessageRoutingBuilder;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.DATA;

public class RoadSideUnitApp extends AbstractApplication<RoadSideUnitOperatingSystem>
        implements CommunicationApplication {

    @Override
    public void onStartup() {
    // Enable the radio so RSU can receive messages (matching the VehApp configuration)
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

    getLog().infoSimTime(this, "RSU App started with both ad-hoc and cellular modules enabled.");
}

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "RSU App shutting down.");
    }

    @Override
    public void onMessageReceived(@Nonnull ReceivedV2xMessage receivedMessage) {
        V2xMessage msg = receivedMessage.getMessage();
        if (msg instanceof VehInfoMsg vehMsg) {
            getLog().infoSimTime(this, "Received VehInfoMsg: " + vehMsg.toString());

            forwardToFog(vehMsg);
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

    private void forwardToFog(VehInfoMsg vehMsg) {
        try {
            MessageRouting routing = getOs().getCellModule().createMessageRouting()
                .destination("fog1")
                .topological()
                .build();
            // Build a new message with the cellular routing
            VehInfoMsg forwardMsg = new VehInfoMsg(routing,
                vehMsg.getTimeStamp(),
                vehMsg.getSenderName(),
                vehMsg.getSenderPosition(),
                vehMsg.getHeading(),
                vehMsg.getSpeed(),
                vehMsg.getLaneId());
            // Send the message using the cell module
            getOs().getCellModule().sendV2xMessage(forwardMsg);
            getLog().infoSimTime(this, "Forwarded message to Fog Node via topologically-scoped unicast.");
        } catch (Exception e) {
            // Log the error without passing the exception as a separate argument.
            getLog().infoSimTime(this, "Error setting fog node IP address: " + e.getMessage());
        }
    }
}
