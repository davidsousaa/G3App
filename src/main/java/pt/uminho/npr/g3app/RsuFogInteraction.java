package pt.uminho.npr.g3app;

import org.eclipse.mosaic.interactions.application.ApplicationInteraction ;

public class RsuFogInteraction extends ApplicationInteraction {
    private final String content;
    private final String sourceUnitId;

    public RsuFogInteraction(long timestamp, String targetUnitId, String content, 
                             String sourceUnitId) {
        super(timestamp, targetUnitId);
        this.content = content;
        this.sourceUnitId = sourceUnitId;
    }

    public String getContent() {
        return content;
    }

    public String getSourceUnitId() {
        return sourceUnitId;
    }

    @Override
    public String toString() {
        return "RsuToFogInteraction{" + "content='" + content + '\'' + '}';
    }
}
