package pt.uminho.npr.g3app;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.annotation.Nonnull;

import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

/*
 * timestamp
 * senderID
 * sender position
 */
public class ChangeRoute extends V2xMessage {

    private final EncodedPayload payload;
    private final long timeStamp;
    private final String senderName;

    public ChangeRoute(
            final MessageRouting routing,
            final String senderName,
            final GeoPoint pos,
            final long time) {

        super(routing);
        this.timeStamp = time;
        this.senderName = senderName;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream(); final DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeLong(timeStamp);
            dos.writeUTF(senderName);
            payload = new EncodedPayload(baos.toByteArray(), baos.size());
        } catch (IOException e) {
            throw new RuntimeException("Error creating ChangeRoute payload", e);
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

    @Override
    public String toString() {
        return "RSUHello{"
                + "timeStamp=" + timeStamp
                + ", senderName='" + senderName + '\''
                + '}';
    }

}
