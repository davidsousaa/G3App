package pt.uminho.npr.g3app;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.annotation.Nonnull;

import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.SerializationUtils;

/*
 * timestamp
 * senderID
 * sender position
 * receiverID
 * warning
 */
public class WarningMsg extends V2xMessage {

    private final EncodedPayload payload;
    private final long timeStamp;
    private final String senderName;
    private final GeoPoint senderPos;
    private final String destination;
    private final boolean rsuConnected;
    private final String nextHop;
    private final String warningMessage;

    public WarningMsg(
            final MessageRouting routing,
            final long time,
            final String senderName,
            final GeoPoint pos,
            final String destination,
            final boolean rsuConnected,
            final String nextHop,
            final String warningMessage) {

        super(routing);
        this.timeStamp = time;
        this.senderName = senderName;
        this.senderPos = pos;
        this.destination = destination;
        this.rsuConnected = rsuConnected;
        this.nextHop = nextHop;
        this.warningMessage = warningMessage;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream(); final DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeLong(timeStamp);
            dos.writeUTF(senderName);
            SerializationUtils.encodeGeoPoint(dos, senderPos);
            dos.writeUTF(destination);
            dos.writeBoolean(rsuConnected);
            dos.writeUTF(nextHop);
            dos.writeUTF(warningMessage);
            payload = new EncodedPayload(baos.toByteArray(), baos.size());
        } catch (IOException e) {
            throw new RuntimeException("Error creating WarningMsg payload", e);
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

    public GeoPoint getSenderPosition() {
        return senderPos;
    }

    public String getDestination() {
        return destination;
    }

    public boolean getRsuConnected() {
        return rsuConnected;
    }

    public String getNextHop() {
        return nextHop;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    @Override
    public String toString() {
        return "WarningMsg{"
                + "timeStamp=" + timeStamp
                + ", senderName='" + senderName + '\''
                + ", senderPos=" + senderPos
                + ", destination='" + destination + '\''
                + ", rsuConnected=" + rsuConnected
                + ", nextHop='" + nextHop + '\''
                + ", warningMessage='" + warningMessage + '\''
                + '}';
    }
}
