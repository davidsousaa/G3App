package pt.uminho.npr.g3app;

import org.eclipse.mosaic.interactions.application.ApplicationInteraction ;

public class RsuFogInteraction extends ApplicationInteraction {
    private final String content;

    public RsuFogInteraction(long timestamp, String targetUnitId, String content) {
        super(timestamp, targetUnitId);
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "RsuToFogInteraction{" + "content='" + content + '\'' + '}';
    }
}
