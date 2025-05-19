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




public class RoadSideUnitApp extends AbstractApplication<RoadSideUnitOperatingSystem>
        implements CommunicationApplication, MosaicApplication {

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

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "RSU App shutting down.");
    }

    @Override
    public void onMessageReceived(@Nonnull ReceivedV2xMessage receivedMessage) {
        V2xMessage msg = receivedMessage.getMessage();
    
        if (msg instanceof VehInfoMsg) {
            String sender = ((VehInfoMsg) msg).getSenderName();
            getLog().infoSimTime(this, "RSU: Received VehInfoMsg from " + sender);
    
            RsuFogInteraction interaction = new RsuFogInteraction(getOs().getSimulationTime(), "fog_1", "TO: fog_1 | Forwarded VehInfoMsg from " + sender + " | " + msg.toString());
    
            getOs().sendInteractionToRti(interaction);
            getLog().infoSimTime(this, "RSU: Sent interaction to fog1: " + interaction.getContent());
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
