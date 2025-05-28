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
    private final String receiverName;
    private final String warningMessage;


    public WarningMsg(
        final MessageRouting routing,
        final long time,
        final String senderName,
        final GeoPoint pos,
        final String receiverName,
        final String warningMessage) {

        super(routing);
        this.timeStamp = time;
        this.senderName = senderName;
        this.senderPos = pos;
        this.receiverName = receiverName;
        this.warningMessage = warningMessage;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeLong(timeStamp);
            dos.writeUTF(senderName);
            SerializationUtils.encodeGeoPoint(dos, senderPos);
            dos.writeUTF(receiverName);
            dos.writeUTF(warningMessage);
            this.payload = EncodedPayload.of(baos.toByteArray());
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
    
    public String getReceiverName() {
        return receiverName;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    @Override
    public String toString() {
        return "WarningMessage{" +
                "timeStamp=" + timeStamp +
                ", senderName='" + senderName + '\'' +
                ", senderPos=" + senderPos +
                ", receiverName='" + receiverName + '\'' +
                ", warningMessage='" + warningMessage + '\'' +
                '}';
    }
}
