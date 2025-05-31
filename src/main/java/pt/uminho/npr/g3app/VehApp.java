package pt.uminho.npr.g3app;

import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.AdHocModuleConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.ambassador.simulation.navigation.INavigationModule;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.enums.AdHocChannel;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.routing.RoutingParameters;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.routing.RoutingResponse;
import org.eclipse.mosaic.lib.routing.util.ReRouteSpecificConnectionsCostFunction;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

public class VehApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication, CommunicationApplication {

    private final long MsgDelay = 200 * TIME.MILLI_SECOND;
    private final int Power = 50;
    private final double Distance = 140.0;

    private int setVal;
    private boolean changedRoute = false;
    private final GeoPoint roadblockTriggerPoint = GeoPoint.latLon(41.551134, -8.411374);
    private final double roadblockTriggerRadius = 10.0;
    private boolean roadblockTriggered = false;

    private double vehHeading;
    private double vehSpeed;
    private int vehLane;

    private Map<String, VehInfoMsg> neighbors = new ConcurrentHashMap<>();
    private Map<String, Long> neighborsTimestamps = new ConcurrentHashMap<>();
    private Map<String, Long> neighborsRSUTimestamps = new ConcurrentHashMap<>();
    private Map<String, RSUHello> neighborsRSU = new ConcurrentHashMap<>();
    private final Long NeighborTimeout = 500 * TIME.MILLI_SECOND;
    private final Long RSUTimeout = 800 * TIME.MILLI_SECOND;

    @Override
    public void onShutdown() {
        //writeNeighborsToCsv();
        getLog().infoSimTime(this, "onShutdown");
        getOs().getAdHocModule().disable();
    }

    @Override
    public void onStartup() {
        getOs().getAdHocModule().enable(new AdHocModuleConfiguration()
                .addRadio()
                .channel(AdHocChannel.CCH)
                .power(Power)
                .distance(Distance)
                .create());

        getLog().infoSimTime(this, "onStartup: Set up");
        if (getOs().getId().equals("veh_75")) {
            getOs().requestVehicleParametersUpdate()
                    .changeColor(Color.RED)
                    .apply();
        }
        setVal = 0;
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + MsgDelay, this);
    }

    @Override
    public void processEvent(Event arg0) throws Exception {
        getLog().infoSimTime(this, "processEvent");
        if (setVal == 1) {
            sendVehInfoMsg();
        }
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + MsgDelay, this);
    }

    @Override
    public void onMessageReceived(@Nonnull ReceivedV2xMessage receivedMessage) {
        //getLog().infoSimTime(this, "onMessageReceived");
        //TODO: process received message
        V2xMessage msg = receivedMessage.getMessage();

        switch (msg) {
            case VehInfoMsg vehInfoMsg -> {

                if (vehInfoMsg.getSenderName().equals(getOs().getId())) {
                    return;
                }
                updateNeighbors(vehInfoMsg);

                //se a flag rsuconnected for true, dar drop. Se for false e for o melhor vizinho, dar forward para o RSU. Senão, esperar pelo timeout, se não receber uma mensagem igual a uma que tenha no buffer, enviar ele para o melhor neighbor. Falta criar função semelhante à sendVehInfoMsg para dar o forward, ou incorporar isso aqui
                if (vehInfoMsg.getRsuConnected() || !vehInfoMsg.getNextHop().equals(getOs().getId())) {
                    // Ignore messages from the RSU or messages that are not meant for this vehicle
                    return;
                } else {
                    getLog().infoSimTime(this, "Forwarding VehInfoMsg: " + vehInfoMsg.toString());
                    forwardMessage(vehInfoMsg);
                }
            }
            case RSUHello rsuHello ->
                updateRSUNeighbors(rsuHello);
            case WarningMsg warningMsg -> {
                if (warningMsg.getSenderName().equals(getOs().getId()) || warningMsg.getRsuConnected() || !warningMsg.getNextHop().equals(getOs().getId())) {
                    return;
                } else {
                    getLog().infoSimTime(this, "Forwarding WarningMsg: " + warningMsg.toString());
                    forwardMessage(warningMsg);
                }

            }
            case RerouteMsg rerouteMsg -> {
                getLog().infoSimTime(this, "Received RerouteMsg: " + rerouteMsg.toString());
                if (!changedRoute) {
                    changedRoute = true;
                    circumnavigateAffectedRoad("50952691#2", 0.0);
                } else {
                    getLog().warnSimTime(this, "Reroute already applied, ignoring: " + rerouteMsg.toString());
                }
            }
            default -> {
            }
        }
        // log neighbors table to output.csv (can be done in onShutdown())
    }

    private void circumnavigateAffectedRoad(final String affectedRoadId, double causedSpeed) {
        ReRouteSpecificConnectionsCostFunction myCostFunction = new ReRouteSpecificConnectionsCostFunction();
        myCostFunction.setConnectionSpeedMS(affectedRoadId, causedSpeed);

        INavigationModule navigationModule = getOs().getNavigationModule();

        RoutingParameters routingParameters = new RoutingParameters().costFunction(myCostFunction);

        RoutingResponse response = navigationModule.calculateRoutes(new RoutingPosition(navigationModule.getTargetPosition()), routingParameters);

        CandidateRoute newRoute = response.getBestRoute();
        if (newRoute != null) {
            getLog().infoSimTime(this, "Alterar rota para evitar: " + affectedRoadId);
            navigationModule.switchRoute(newRoute);
        } else {
            getLog().warnSimTime(this, "Nenhuma rota alternativa encontrada para evitar: " + affectedRoadId);
        }
    }

    public void updateRSUNeighbors(RSUHello rsuHello) {
        String id = rsuHello.getSenderName();
        long currentTime = getOs().getSimulationTime();

        neighborsRSU.put(id, rsuHello);
        neighborsRSUTimestamps.put(id, currentTime);

        if (getOs().getId().equals("veh_5")) {
            getLog().infoSimTime(this, "Received RSUHello: " + rsuHello.toString());
            getLog().infoSimTime(this, "RSU connected: " + neighborsRSUTimestamps.keySet().toString() + " | " + neighborsRSU.keySet().toString());
        }

        removeOldRSUs();
    }

    public void forwardMessage(V2xMessage message) {
        boolean rsuConnected = isRSUConnected();
        MessageRouting routing = getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoBroadCast();
        String rsu = getRSU();

        if (rsuConnected) {
            switch (message) {
                case VehInfoMsg vehInfoMsg -> {
                    VehInfoMsg forwardedMsg = new VehInfoMsg(routing, vehInfoMsg.getTimeStamp(), vehInfoMsg.getSenderName(), vehInfoMsg.getSenderPosition(), vehInfoMsg.getHeading(), vehInfoMsg.getSpeed(), vehInfoMsg.getLaneId(), vehInfoMsg.getDestination(), rsuConnected, rsu);

                    getOs().getAdHocModule().sendV2xMessage(forwardedMsg);

                    getLog().infoSimTime(this, "Forwarded VehInfoMsg to RSU: " + vehInfoMsg.toString());
                }
                case WarningMsg warningMsg -> {
                    WarningMsg forwardedWarning = new WarningMsg(routing, warningMsg.getTimeStamp(), warningMsg.getSenderName(), warningMsg.getSenderPosition(), warningMsg.getDestination(), rsuConnected, rsu, warningMsg.getWarningMessage());

                    getOs().getAdHocModule().sendV2xMessage(forwardedWarning);

                    getLog().infoSimTime(this, "Forwarded WarningMsg to RSU: " + forwardedWarning.toString());
                }
                default -> {
                }
            }
        } else {
            getLog().warnSimTime(this, "Cannot forward message, RSU is not connected.");
        }
    }

    public void updateNeighbors(VehInfoMsg vehInfoMsg) {
        String id = vehInfoMsg.getSenderName();
        long currentTime = getOs().getSimulationTime();

        neighbors.put(id, vehInfoMsg);
        neighborsTimestamps.put(id, currentTime);

        removeOldNeighbors();
    }

    // Em vez que fazer os loops, podemos remover apenas quando precisamos de usar os neighbors e, se o timestamp for maior que o timeout, ignore e remove
    public void removeOldNeighbors() {
        long currentTime = getOs().getSimulationTime();
        Iterator<Map.Entry<String, Long>> iterator = neighborsTimestamps.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            long timestamp = entry.getValue();

            if (currentTime - timestamp > NeighborTimeout) {
                String id = entry.getKey();
                iterator.remove();
                neighbors.remove(id);
            }
        }
    }

    private void writeNeighborsToCsv() {
        String filename = "neighbors_" + getOs().getId() + ".csv"; // One file per RSU/vehicle
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("NeighborID,LastSeenTime");
            for (Map.Entry<String, Long> entry : neighborsTimestamps.entrySet()) {
                writer.println(entry.getKey() + "," + entry.getValue());
            }
            getLog().infoSimTime(this, "Neighbors table written to: " + filename);
        } catch (IOException e) {
            getLog().warnSimTime(this, "Error writing neighbors CSV: " + e.getMessage());
        }
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission arg0) {
        getLog().infoSimTime(this, "onMessageTransmitted");
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        getLog().infoSimTime(this, "onVehicleUpdated");
        if (setVal == 0) {
            setVal = 1;
        }
        this.vehHeading = updatedVehicleData.getHeading().doubleValue();
        this.vehSpeed = updatedVehicleData.getSpeed();
        this.vehLane = updatedVehicleData.getRoadPosition().getLaneIndex();

        GeoPoint currentPosition = updatedVehicleData.getPosition();

        if (getOs().getId().equals("veh_75") && !roadblockTriggered && currentPosition.distanceTo(roadblockTriggerPoint) < roadblockTriggerRadius) {
            getLog().infoSimTime(this, "Roadblock triggered at position: " + currentPosition.toString());
            roadblockTriggered = true;
            sendWarningMsg();
        }

    }

    public void sendWarningMsg() {
        MessageRouting routing = getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoBroadCast();
        long time = getOs().getSimulationTime();
        String rsu = this.getRSU();
        boolean isRsuConnected = isRSUConnected();
        WarningMsg warningMsg;

        if (isRsuConnected) {
            warningMsg = new WarningMsg(routing, time, getOs().getId(), getOs().getPosition(), rsu, isRsuConnected, rsu, "Roadblock");
            getOs().getAdHocModule().sendV2xMessage(warningMsg);
            getLog().infoSimTime(this, "Sent WarningMsg to RSU: " + warningMsg.toString());
        } else {
            String bestNeighbor = getBestNeighbor();
            if (bestNeighbor != null) {
                warningMsg = new WarningMsg(routing, time, getOs().getId(), getOs().getPosition(), rsu, isRsuConnected, bestNeighbor, "Roadblock");
                getOs().getAdHocModule().sendV2xMessage(warningMsg);
                getLog().infoSimTime(this, "Sent WarningMsg to best neighbor: " + warningMsg.toString());
            } else {
                getLog().warnSimTime(this, "No suitable neighbor found to forward the message.");
            }
        }
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement arg0) {
        getLog().infoSimTime(this, "onAcknowledgementReceived");

    }

    @Override
    public void onCamBuilding(CamBuilder arg0) {
        getLog().infoSimTime(this, "onCamBuilding");
    }

    private void removeOldRSUs() {
        long currentTime = getOs().getSimulationTime();
        Iterator<Map.Entry<String, Long>> iterator = neighborsRSUTimestamps.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            long timestamp = entry.getValue();

            if (currentTime - timestamp > RSUTimeout && neighborsRSUTimestamps.size() > 1) {
                iterator.remove();
                neighborsRSU.remove(entry.getKey());
            }
        }
    }

    private String getRSU() {
        removeOldRSUs();

        if (!neighborsRSUTimestamps.isEmpty()) {
            for (String rsuId : neighborsRSUTimestamps.keySet()) {
                return rsuId;
            }
        }

        return null;
    }

    private boolean isRSUConnected() {
        removeOldRSUs();
        long currentTime = getOs().getSimulationTime();
        for (Map.Entry<String, Long> entry : neighborsRSUTimestamps.entrySet()) {
            long timestamp = entry.getValue();

            if (currentTime - timestamp > RSUTimeout) {
                return false;
            }
        }
        return true;
    }

    public String getBestNeighbor() {
        removeOldNeighbors();
        String bestNeighbor = null;
        double bestDistance = Double.MAX_VALUE;

        for (Map.Entry<String, VehInfoMsg> entry : neighbors.entrySet()) {
            String neighborId = entry.getKey();
            VehInfoMsg neighborMsg = entry.getValue();
            GeoPoint neighborPos = neighborMsg.getSenderPosition();
            GeoPoint rsuPos = neighborsRSU.entrySet().iterator().next().getValue().getSenderPos();

            if (neighborId.equals(getOs().getId())) {
                continue;
            }

            double distance = calculateDistance(rsuPos.getLatitude(), rsuPos.getLongitude(), neighborPos.getLatitude(), neighborPos.getLongitude());

            if (bestNeighbor == null || distance < bestDistance) {
                bestNeighbor = neighborId;
                bestDistance = distance;
            } else if (distance == bestDistance) {
                bestNeighbor = neighborId;
            }
        }

        return bestNeighbor;
    }

    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void sendVehInfoMsg() {
        MessageRouting routing = getOs().getAdHocModule().createMessageRouting().viaChannel(AdHocChannel.CCH).topoBroadCast();
        long time = getOs().getSimulationTime();
        String rsu = this.getRSU();
        VehInfoMsg message = null;
        if (rsu == null) { //Caso inicial, onde podem ainda não ter recebido mensagens de RSUHello do primeiro RSU
            rsu = "rsu_0";
        }
        boolean isRsuConnected = isRSUConnected();

        if (isRsuConnected) {
            message = new VehInfoMsg(routing, time, getOs().getId(), getOs().getPosition(), this.vehHeading, this.vehSpeed, this.vehLane, rsu, isRsuConnected, rsu);

            getOs().getAdHocModule().sendV2xMessage(message);
        } else {
            String bestNeighbor = getBestNeighbor();
            if (bestNeighbor != null) {
                message = new VehInfoMsg(routing, time, getOs().getId(), getOs().getPosition(), this.vehHeading, this.vehSpeed, this.vehLane, rsu, isRsuConnected, bestNeighbor);
                getOs().getAdHocModule().sendV2xMessage(message);
            } else {
                getLog().warnSimTime(this, "No suitable neighbor found to forward the message.");
            }
        }

        getLog().infoSimTime(this, "Sent VehInfoMsg: " + message.toString());
    }
}
