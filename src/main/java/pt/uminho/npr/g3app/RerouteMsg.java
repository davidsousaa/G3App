package pt.uminho.npr.g3app;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.annotation.Nonnull;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

/*
 * timestamp
 * senderID
 * sender position
 */
public class RerouteMsg extends V2xMessage {

    private final EncodedPayload payload;
    private final long timeStamp;
    private final String senderName;
    private final String newRoute;

    public RerouteMsg(
            final MessageRouting routing,
            final String senderName,
            final long time,
            final String newRoute) {

        super(routing);
        this.timeStamp = time;
        this.senderName = senderName;
        this.newRoute = newRoute;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream(); final DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeLong(timeStamp);
            dos.writeUTF(senderName);
            dos.writeUTF(newRoute);
            payload = new EncodedPayload(baos.toByteArray(), baos.size());
        } catch (IOException e) {
            throw new RuntimeException("Error creating RerouteMsg payload", e);
        }
    }

    @Nonnull
    @Override
    public EncodedPayload getPayload() {
        return payload;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getNewRoute() {
        return newRoute;
    }

    @Override
    public String toString() {
        return "RerouteMsg{"
                + "timeStamp=" + timeStamp
                + ", senderName='" + senderName + '\''
                + ", newRoute='" + newRoute + '\''
                + '}';
    }

}
