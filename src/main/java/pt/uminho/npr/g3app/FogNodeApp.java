package pt.uminho.npr.g3app;

import javax.annotation.Nonnull;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.MosaicApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.interactions.application.ApplicationInteraction;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.objects.traffic.SumoTraciResult;
import org.eclipse.mosaic.rti.DATA;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CellModuleConfiguration;


public class FogNodeApp extends AbstractApplication<ServerOperatingSystem> implements CommunicationApplication, MosaicApplication {

    

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
    public void onAcknowledgementReceived(org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement ack) {
        // Not used in this example
    }

    @Override
    public void onInteractionReceived(ApplicationInteraction interaction) {
        if (interaction instanceof RsuFogInteraction) {
            RsuFogInteraction rsuMsg = (RsuFogInteraction) interaction;

            if (rsuMsg.getContent().contains("TO: fog_1")) {
                getLog().infoSimTime(this, "Fog Node received targeted RsuFogInteraction: " + rsuMsg.getContent());
                // send a responde back
                RsuFogInteraction response = new RsuFogInteraction(getOs().getSimulationTime(), rsuMsg.getSenderId(), "Received by fog_1");
                getOs().sendInteractionToRti(response);
            } else {
                getLog().infoSimTime(this, "Fog Node received RsuFogInteraction not targeted to this node: " + rsuMsg.getContent());
            }
        } else {
            getLog().infoSimTime(this, "Fog Node received unknown interaction type: " + interaction.toString());
        }
    }

    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
        // Not used in this example
    }

}
