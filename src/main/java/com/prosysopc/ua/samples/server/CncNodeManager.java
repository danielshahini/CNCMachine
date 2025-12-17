package com.prosysopc.ua.samples.server;

import com.prosysopc.ua.StatusException;
import com.prosysopc.ua.ValueRanks;
import com.prosysopc.ua.nodes.UaMethod;
import com.prosysopc.ua.nodes.UaNode;
import com.prosysopc.ua.server.*;
import com.prosysopc.ua.server.nodes.*;
import com.prosysopc.ua.stack.builtintypes.*;
import com.prosysopc.ua.stack.core.Argument;
import com.prosysopc.ua.stack.core.Identifiers;
import com.prosysopc.ua.stack.core.StatusCodes;

import com.prosysopc.ua.stack.builtintypes.StatusCode;
import com.prosysopc.ua.stack.builtintypes.Variant;

import java.util.*;

/**
 * Alternative implementation of the CNC Machine NodeManager (Version 2).
 * Same tags & methods as required by the assignment, but different internal structure.
 */
public class CncNodeManager extends NodeManagerUaNode {

    public static final String NAMESPACE = "http://example.com/CNC/v2";

    private UaObjectNode machineNode;

    private UaVariableNode machineStatusVar;
    private UaVariableNode spindleTargetVar;
    private UaVariableNode spindleActualVar;
    private UaVariableNode feedTargetVar;
    private UaVariableNode feedActualVar;
    private UaVariableNode toolLifeVar;
    private UaVariableNode coolantTempVar;
    private UaVariableNode xPosVar, yPosVar, zPosVar;
    private UaVariableNode productionProgressVar;
    private UaVariableNode alarmMessageVar;
    private UaVariableNode surfaceTargetVar, surfaceActualVar;

    private UaVariableNode goodPartsVar, badPartsVar, totalPartsVar;
    private UaVariableNode cuttingForceXVar, cuttingForceYVar, cuttingForceZVar;
    private UaVariableNode machiningPhaseVar;
    private UaVariableNode orderVar, articleVar, quantityVar;

    private UaVariableNode targetCoolantFlowVar, actualCoolantFlowVar;
    private UaVariableNode targetCycleTimeVar, actualCycleTimeVar;

    private final Random rng = new Random();

    private final AlarmEngine alarmEngine = new AlarmEngine();

    private final Map<String, UaVariableNode> tagMap = new HashMap<>();

    public CncNodeManager(UaServer server, String namespaceUri) {
        super(server, namespaceUri);
    }

    @Override
    protected void init() throws StatusException {
        super.init();
        createMachineObject();
        registerAllVariables();
        registerAllMethods();
    }

    private void createMachineObject() throws StatusException {
        int ns = getNamespaceIndex();

        machineNode = new UaObjectNode(
                this,
                new NodeId(ns, "CncMachine"),
                new QualifiedName(ns, "CncMachine"),
                LocalizedText.english("CNC Machining Center (V2)")
        );

        addNodeAndReference(
                getServer().getNodeManagerRoot().getObjectsFolder(),
                machineNode,
                Identifiers.Organizes
        );
    }

    private void registerAllVariables() throws StatusException {
        createCoreStatusVariables();
        createPositionAndSurfaceVariables();
        createProductionAndQualityVariables();
        createCuttingForceAndCoolantVariables();
        createMetaInformationVariables();
    }

    private void createCoreStatusVariables() throws StatusException {
        machineStatusVar   = createAndRegister("MachineStatus", "Running");
        spindleTargetVar   = createAndRegister("TargetSpindleSpeed", 8500.0);
        spindleActualVar   = createAndRegister("ActualSpindleSpeed", 8487.0);
        feedTargetVar      = createAndRegister("TargetFeedRate", 1200.0);
        feedActualVar      = createAndRegister("ActualFeedRate", 1198.0);
        toolLifeVar        = createAndRegister("ToolLifeRemaining", 73.2);
        coolantTempVar     = createAndRegister("CoolantTemperature", 22.5);
    }

    private void createPositionAndSurfaceVariables() throws StatusException {
        xPosVar = createAndRegister("X", 125.847);
        yPosVar = createAndRegister("Y", 89.234);
        zPosVar = createAndRegister("Z", -45.678);

        surfaceTargetVar = createAndRegister("TargetSurfaceFinish", 0.8);
        surfaceActualVar = createAndRegister("ActualSurfaceFinish", 0.75);
    }

    private void createProductionAndQualityVariables() throws StatusException {
        productionProgressVar = createAndRegister("ProductionOrderProgress", 57.5);
        alarmMessageVar       = createAndRegister("AlarmMessage", "OK");

        goodPartsVar  = createAndRegister("GoodParts", 2847.0);
        badPartsVar   = createAndRegister("BadParts", 23.0);
        totalPartsVar = createAndRegister("TotalParts", 2870.0);

        orderVar    = createAndRegister("ProductionOrder", "PO-2024-AERO-0876");
        articleVar  = createAndRegister("Article", "ART-TB-7075-T6");
        quantityVar = createAndRegister("OrderQuantity", 120.0);

        machiningPhaseVar = createAndRegister("MachiningPhase", "Roughing");
    }

    private void createCuttingForceAndCoolantVariables() throws StatusException {
        cuttingForceXVar = createAndRegister("CuttingForceX", 245.7);
        cuttingForceYVar = createAndRegister("CuttingForceY", 189.3);
        cuttingForceZVar = createAndRegister("CuttingForceZ", 567.8);

        targetCoolantFlowVar = createAndRegister("TargetCoolantFlow", 25.0);
        actualCoolantFlowVar = createAndRegister("ActualCoolantFlow", 24.8);

        targetCycleTimeVar = createAndRegister("TargetCycleTime", 75.0);
        actualCycleTimeVar = createAndRegister("ActualCycleTime", 73.2);
    }

    private void createMetaInformationVariables() throws StatusException {
        createAndRegister("MachineName", "PrecisionCraft VMC-850 #3");
        createAndRegister("MachineSerialNumber", "VMC850-2023-003");
        createAndRegister("Plant", "Munich Precision Manufacturing");
        createAndRegister("ProductionSegment", "Aerospace Components");
        createAndRegister("ProductionLine", "5-Axis Machining Cell C");
        createAndRegister("GoodPartsOrder", 67.0);
        createAndRegister("BadPartsOrder", 2.0);
        createAndRegister("TotalPartsOrder", 69.0);
    }

    private PlainVariable<?> createAndRegister(String tagName, Object initialValue) throws StatusException {
        int ns = getNamespaceIndex();
        NodeId id = new NodeId(ns, tagName);
        QualifiedName browseName = new QualifiedName(ns, tagName);
        LocalizedText displayName = LocalizedText.english(tagName);

        PlainVariable<Object> var = new PlainVariable<>(this, id, browseName, displayName);

        var.setDataTypeId(resolveDataType(initialValue));
        var.addReference(Identifiers.HasTypeDefinition, Identifiers.BaseDataVariableType, false);
        var.setDescription(LocalizedText.english("CNC Tag: " + tagName));
        var.setValue(new Variant(initialValue));

        addNodeAndReference(machineNode, var, Identifiers.HasComponent);

        tagMap.put(tagName, var);
        return var;
    }

    private NodeId resolveDataType(Object value) {
        if (value instanceof String)  return Identifiers.String;
        if (value instanceof Integer) return Identifiers.Int32;
        if (value instanceof Double || value instanceof Float) return Identifiers.Double;
        if (value instanceof Boolean) return Identifiers.Boolean;
        return Identifiers.BaseDataType;
    }

    private void registerAllMethods() throws StatusException {
        final int ns = getNamespaceIndex();

        java.util.function.Function<String, UaMethodNode> methodFactory = methodName -> {
            UaMethodNode node = new UaMethodNode(
                    this,
                    new NodeId(ns, methodName),
                    new QualifiedName(ns, methodName),
                    LocalizedText.english(methodName)
            );
            node.setExecutable(true);
            node.setUserExecutable(true);
            try {
                addNodeAndReference(machineNode, node, Identifiers.HasComponent);
            } catch (StatusException e) {
                throw new RuntimeException(e);
            }
            return node;
        };

        UaMethodNode startMethod       = methodFactory.apply("StartMachine");
        UaMethodNode stopMethod        = methodFactory.apply("StopMachine");
        UaMethodNode maintenanceMethod = methodFactory.apply("EnterMaintenanceMode");
        UaMethodNode resetMethod       = methodFactory.apply("ResetCounters");
        UaMethodNode homeAxesMethod    = methodFactory.apply("HomeAxes");

        UaMethodNode toolChangeMethod  = methodFactory.apply("ToolChange");
        addInputArgumentsProperty(toolChangeMethod, new Argument[]{
                new Argument("ToolNumber", Identifiers.Int32, ValueRanks.Scalar, null,
                        LocalizedText.english("Tool number to switch to"))
        });

        UaMethodNode loadProgramMethod = methodFactory.apply("LoadCncProgram");
        addInputArgumentsProperty(loadProgramMethod, new Argument[]{
                new Argument("ProgramName", Identifiers.String, ValueRanks.Scalar, null,
                        LocalizedText.english("Program name"))
        });

        UaMethodNode loadOrderMethod   = methodFactory.apply("LoadProductionOrder");
        addInputArgumentsProperty(loadOrderMethod, new Argument[]{
                new Argument("Order",    Identifiers.String, ValueRanks.Scalar, null, LocalizedText.english("Order number")),
                new Argument("Article",  Identifiers.String, ValueRanks.Scalar, null, LocalizedText.english("Article identifier")),
                new Argument("Quantity", Identifiers.Double, ValueRanks.Scalar, null, LocalizedText.english("Target quantity"))
        });

        MethodManagerUaNode mm = new MethodManagerUaNode(this);
        mm.addCallListener(new CallableListener() {
            @Override
            public boolean onCall(ServiceContext ctx,
                                  NodeId objectId,
                                  UaNode objectNode,
                                  NodeId methodId,
                                  UaMethod method,
                                  Variant[] inputArguments,
                                  StatusCode[] inputArgResults,
                                  DiagnosticInfo[] inputArgDiag,
                                  Variant[] outputArguments) throws StatusException {

                try {
                    if (methodId.equals(startMethod.getNodeId())) {
                        handleStart();
                    } else if (methodId.equals(stopMethod.getNodeId())) {
                        handleStop();
                    } else if (methodId.equals(maintenanceMethod.getNodeId())) {
                        handleEnterMaintenance();
                    } else if (methodId.equals(resetMethod.getNodeId())) {
                        handleResetCounters();
                    } else if (methodId.equals(homeAxesMethod.getNodeId())) {
                        handleHomeAxes();
                    } else if (methodId.equals(toolChangeMethod.getNodeId())) {
                        int toolNum = extractToolNumber(inputArguments);
                        handleToolChange(toolNum);
                    } else if (methodId.equals(loadProgramMethod.getNodeId())) {
                        String programName = (inputArguments != null && inputArguments.length > 0)
                                ? String.valueOf(inputArguments[0].getValue())
                                : "DefaultProgram";
                        handleLoadProgram(programName);
                    } else if (methodId.equals(loadOrderMethod.getNodeId())) {
                        handleLoadOrder(inputArguments);
                    } else {
                        return false;
                    }
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
        });
    }

    private void addInputArgumentsProperty(UaMethodNode methodNode, Argument[] args) throws StatusException {
        int ns = getNamespaceIndex();

        PlainProperty inputProp = new PlainProperty(
                this,
                new NodeId(ns, methodNode.getBrowseName().getName() + "_InputArgs"),
                new QualifiedName(ns, "InputArguments"),
                LocalizedText.english("InputArguments")
        );

        inputProp.setDataTypeId(Identifiers.Argument);
        inputProp.setValueRank(ValueRanks.OneDimension);
        inputProp.addReference(Identifiers.HasTypeDefinition, Identifiers.PropertyType, false);
        inputProp.setValue(new Variant(args));

        addNodeAndReference(methodNode, inputProp, Identifiers.HasProperty);
    }

    private void handleStart() {
        updateStatus("Starting");
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                updateStatus("Running");
            }
        }, 2000);
        System.out.println("[OPC-UA] Machine start sequence triggered (V2).");
    }

    private void handleStop() {
        updateStatus("Stopping");
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                updateStatus("Stopped");
            }
        }, 2000);
        System.out.println("[OPC-UA] Machine stop sequence triggered (V2).");
    }

    private void handleEnterMaintenance() {
        updateStatus("Maintenance");
        System.out.println("[OPC-UA] Machine switched to Maintenance mode.");
    }

    private void handleResetCounters() {
        try {
            goodPartsVar.setValue(new Variant(0.0));
            badPartsVar.setValue(new Variant(0.0));
            totalPartsVar.setValue(new Variant(0.0));
            productionProgressVar.setValue(new Variant(0.0));
            System.out.println("[OPC-UA] All global counters have been reset.");
        } catch (StatusException e) {
            e.printStackTrace();
        }
    }

    private void handleToolChange(int toolNumber) throws StatusException {
        System.out.println("[OPC-UA] Tool change requested → Tool #" + toolNumber);
        // simple: reset tool life to 100%
        toolLifeVar.setValue(new Variant(100.0));
    }

    private void handleLoadProgram(String programName) {
        System.out.println("[OPC-UA] CNC program loaded: " + programName);
    }

    private void handleHomeAxes() {
        try {
            xPosVar.setValue(new Variant(0.0));
            yPosVar.setValue(new Variant(0.0));
            zPosVar.setValue(new Variant(0.0));
            updateStatus("Homed");
            System.out.println("[OPC-UA] Axes homed to reference position.");
        } catch (StatusException e) {
            e.printStackTrace();
        }
    }

    private void handleLoadOrder(Variant[] args) {
        String orderId = "";
        String articleId = "";
        double qty = 0.0;

        if (args != null) {
            if (args.length > 0 && args[0] != null && args[0].getValue() != null) {
                orderId = String.valueOf(args[0].getValue());
            }
            if (args.length > 1 && args[1] != null && args[1].getValue() != null) {
                articleId = String.valueOf(args[1].getValue());
            }
            if (args.length > 2 && args[2] != null && args[2].getValue() != null) {
                Object raw = args[2].getValue();
                if (raw instanceof Number) {
                    qty = ((Number) raw).doubleValue();
                } else {
                    qty = Double.parseDouble(String.valueOf(raw));
                }
            }
        }

        try {
            orderVar.setValue(new Variant(orderId));
            articleVar.setValue(new Variant(articleId));
            quantityVar.setValue(new Variant(qty));
            productionProgressVar.setValue(new Variant(0.0));
            updateStatus("Order Loaded");

            System.out.printf("[OPC-UA] Production order loaded: %s, article=%s, qty=%.1f%n",
                    orderId, articleId, qty);

        } catch (StatusException e) {
            e.printStackTrace();
        }
    }

    private int extractToolNumber(Variant[] inputArguments) {
        int defaultTool = 1;
        if (inputArguments == null || inputArguments.length == 0) {
            return defaultTool;
        }
        Object v = inputArguments[0].getValue();
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException ex) {
            return defaultTool;
        }
    }

    private void updateStatus(String newStatus) {
        try {
            machineStatusVar.setValue(new Variant(newStatus));
        } catch (StatusException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called periodically to simulate process changes.
     * Public API is unchanged, internal flow is different in V2.
     */
    public void simulateCycle() {
        try {
            double targetRpm   = getDouble(spindleTargetVar);
            double actualRpm   = varyAround(targetRpm, 0.04);
            spindleActualVar.setValue(new Variant(actualRpm));

            double targetFeed  = getDouble(feedTargetVar);
            double actualFeed  = varyAround(targetFeed, 0.03);
            feedActualVar.setValue(new Variant(actualFeed));

            double remainingToolLife = Math.max(getDouble(toolLifeVar) - 0.2, 0.0);
            toolLifeVar.setValue(new Variant(remainingToolLife));

            double targetRa = getDouble(surfaceTargetVar);
            double actualRa = targetRa + (rng.nextDouble() - 0.5) * 0.02;
            surfaceActualVar.setValue(new Variant(actualRa));

            double fx = getDouble(cuttingForceXVar);
            double fy = getDouble(cuttingForceYVar);
            double fz = getDouble(cuttingForceZVar);

            alarmEngine.evaluateAndUpdate(
                    targetRpm,
                    actualRpm,
                    remainingToolLife,
                    targetRa,
                    actualRa,
                    fx, fy, fz
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private double varyAround(double baseValue, double amplitudeFraction) {
        double factor = 1.0 + (rng.nextDouble() - 0.5) * amplitudeFraction * 2;
        return baseValue * factor;
    }

    private double getDouble(UaVariableNode node) throws StatusException {
        return (Double) node.getValue().getValue().getValue();
    }

    private class AlarmEngine {
        private final LinkedList<Double> recentRpmDeviation = new LinkedList<>();

        void evaluateAndUpdate(double targetRpm,
                               double actualRpm,
                               double toolLife,
                               double surfTarget,
                               double surfActual,
                               double fx,
                               double fy,
                               double fz) {

            try {
                double deviation = Math.abs(actualRpm - targetRpm) / targetRpm;
                recentRpmDeviation.add(deviation);
                if (recentRpmDeviation.size() > 3) {
                    recentRpmDeviation.removeFirst();
                }

                boolean threeConsecutiveHigh = recentRpmDeviation.size() == 3 &&
                        recentRpmDeviation.stream().allMatch(d -> d > 0.05);

                double totalForce = Math.sqrt(fx * fx + fy * fy + fz * fz);
                double surfDelta  = Math.abs(surfActual - surfTarget);

                String alarmText = null;

                if (deviation > 0.15) {
                    alarmText = String.format("SpindleSpeed deviation >15%% (actual=%.2f)", actualRpm);
                } else if (threeConsecutiveHigh) {
                    alarmText = "SpindleSpeed deviation >5% for last 3 cycles";
                } else if (toolLife < 10.0) {
                    alarmText = "Tool wear alarm: remaining life <10%";
                } else if (totalForce > 1.5 * 600.0) {
                    alarmText = String.format("Tool breakage suspected: cutting force=%.1f N", totalForce);
                } else if (surfDelta > 0.01) {
                    alarmText = String.format("Dimensional tolerance ±0.01mm exceeded (Δ=%.4f)", surfActual - surfTarget);
                }

                if (alarmText != null) {
                    raiseAlarm(alarmText);
                } else {
                    clearAlarm();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void raiseAlarm(String message) {
            System.out.println("[ALARM] " + message);
            updateStatus("Error");
            try {
                alarmMessageVar.setValue(new Variant(message));
            } catch (StatusException e) {
                e.printStackTrace();
            }
        }

        private void clearAlarm() {
            try {
                alarmMessageVar.setValue(new Variant("OK"));
                // do not override status if machine is already stopped etc.
                if ("Error".equals(machineStatusVar.getValue().getValue().getValue())) {
                    updateStatus("Running");
                }
            } catch (StatusException e) {
                e.printStackTrace();
            }
        }
    }
}
