package pt.uminho.npr.g3app;

import javax.annotation.Nonnull;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.util.scheduling.Event;

public class FogNodeApp extends AbstractApplication<ServerOperatingSystem>
        implements CommunicationApplication {

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Fog Node started.");
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Fog Node shutting down.");
    }

    @Override
    public void processEvent(Event event) {
        // Process periodic tasks, if any.
    }

    @Override
    public void onMessageReceived(@Nonnull ReceivedV2xMessage receivedMessage) {
        getLog().infoSimTime(this, "Fog Node received: " + receivedMessage.getMessage().toString());
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission transmission) {
        getLog().infoSimTime(this, "Fog Node transmitted a message.");
    }

    @Override
    public void onCamBuilding(CamBuilder arg0) {
        getLog().infoSimTime(this, "Fog Node: onCamBuilding");
    }

    @Override
    public void onAcknowledgementReceived(org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement ack) {
        getLog().infoSimTime(this, "Fog Node: ACK received.");
    }
}
