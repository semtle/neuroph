package org.neuroph.netbeans.visual.widgets;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.netbeans.api.visual.action.AcceptProvider;
import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.action.ConnectorState;
import org.netbeans.api.visual.anchor.AnchorFactory;
import org.netbeans.api.visual.anchor.AnchorShape;
import org.netbeans.api.visual.border.BorderFactory;
import org.netbeans.api.visual.layout.LayoutFactory;
import org.netbeans.api.visual.model.ObjectScene;
import org.netbeans.api.visual.model.ObjectSceneEvent;
import org.netbeans.api.visual.model.ObjectSceneEventType;
import org.netbeans.api.visual.model.ObjectSceneListener;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.ConnectionWidget;
import org.netbeans.api.visual.widget.ImageWidget;
import org.netbeans.api.visual.widget.LabelWidget;
import org.netbeans.api.visual.widget.LayerWidget;
import org.netbeans.api.visual.widget.Widget;
import org.netbeans.api.visual.widget.general.IconNodeWidget;
import org.neuroph.core.Connection;
import org.neuroph.core.Layer;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.Neuron;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.learning.LearningRule;
import org.neuroph.netbeans.visual.VisualEditorTopComponent;
import org.neuroph.netbeans.visual.NeuralNetworkEditor;
import org.neuroph.netbeans.visual.popup.MainPopupMenuProvider;
import org.neuroph.netbeans.visual.widgets.actions.KeyboardDeleteAction;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 * This class represents scene specialised for displaying neural networks
 * 
 * TODO: delegirati iscrtavanje widgeta u svaki widget ponaosob:
 *  NeuronWidget - da sam crta sve sto je vezano zanjega
 *  NeuralLayerWidget - da sam crta neurone
 *  NeuralNetworkWidget da crta layere i konekcije
 * 
 *  kad se doda neuron da se osvezi samo layer
 *  kad se doda lejer da ga docrta
 *  
 *  svaka komponenta da ima flag koji pokazuje da li je treba iscrtati ponovo
 *  
 * 
 * @author Zoran Sevarac
 */
public class NeuralNetworkScene extends ObjectScene  {
//http://bits.netbeans.org/dev/javadoc/org-netbeans-api-visual/org/netbeans/api/visual/widget/doc-files/documentation.html

    private LayerWidget mainLayer;          // this layer contains widgets
    private LayerWidget interractionLayer;  // this layer is for drawing connections while connectiong
    private LayerWidget connectionLayer;    // this layer contains connections
  
    private NeuralNetworkWidget neuralNetworkWidget;
    private NeuralNetwork neuralNetwork;
    
    private DataSet dataSet;
    
    IconNodeWidget inputsContainerWidget = null;
    IconNodeWidget outputsContainerWidget = null;
    IconNodeWidget dataSetWidget = null;
    ImageWidget componentsWidgets = null;
  
    LabelWidget dataSetLabel;
    
    
    private boolean waitingClick = false;
    private boolean waitingLayerClick = false;
    
    // neurons and widgets bufferd index - object widget mapping from object scene shoul dbe used instead
    private HashMap<Neuron, NeuronWidget> neuronsAndWidgets = new HashMap<>();
    HashMap<Layer, NeuralLayerWidget> layersAndWidgets = new HashMap<>();
        
    InstanceContent content = new InstanceContent();
    AbstractLookup aLookup = new AbstractLookup(content);
    
    ArrayList<Neuron> neurons = new ArrayList<>();
    ArrayList<Layer> layers = new ArrayList<>();
        
    public static final int TOO_MANY_NEURONS = 100;
    public static final int TOO_MANY_CONNECTIONS = 250;
    
    private NeuralNetworkEditor networkEditor;
    private VisualEditorTopComponent topComponent;
    private ScenePreferences scenePreferences;

    public NeuralNetworkScene(NeuralNetwork neuralNet) {

        this.neuralNetwork = neuralNet;
        neuralNetworkWidget = new NeuralNetworkWidget(this, neuralNet);

        setLayout(LayoutFactory.createOverlayLayout());

        connectionLayer = new LayerWidget(this);    // draw connections
        interractionLayer = new LayerWidget(this); // draw connections while creating them
        mainLayer = new LayerWidget(this);         // holds widget 
        mainLayer.setLayout(LayoutFactory.createVerticalFlowLayout( LayoutFactory.SerialAlignment.CENTER, 20));
                
        networkEditor = new NeuralNetworkEditor(neuralNet);

        scenePreferences = new ScenePreferences();

        LabelWidget neuralNetworkLabel = new LabelWidget(this, "Neural Network");
        neuralNetworkLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        dataSetWidget = new IconNodeWidget(this);
        dataSetLabel = new LabelWidget(this, "DataSet: none (drag n drop to set)");
        dataSetWidget.setBorder(BorderFactory.createRoundedBorder(5, 5, Color.white, Color.black));
        dataSetWidget.setLayout(LayoutFactory.createVerticalFlowLayout( LayoutFactory.SerialAlignment.CENTER, 4));

        dataSetWidget.addChild(dataSetLabel);
        
        mainLayer.addChild(dataSetWidget);
        
        inputsContainerWidget = new IconNodeWidget(this);
    //    inputsContainerWidget.setBorder(BorderFactory.createLineBorder(15));
//        mainLayer.addChild(new LabelWidget(this, "Inputs"));
        dataSetWidget.addChild(inputsContainerWidget);


      //  mainLayer.addChild(neuralNetworkLabel);
        mainLayer.addChild(neuralNetworkWidget);

//        mainLayer.addChild(new LabelWidget(this, "Outputs"));
        outputsContainerWidget = new IconNodeWidget(this);
        outputsContainerWidget.setBorder(BorderFactory.createRoundedBorder(5, 5, Color.white, Color.black));      
        mainLayer.addChild(outputsContainerWidget);

        addChild(mainLayer);
        addChild(connectionLayer);
        addChild(interractionLayer);

        addObject(neuralNetwork, neuralNetworkWidget);

        getActions().addAction(ActionFactory.createPanAction());
        getActions().addAction(ActionFactory.createMouseCenteredZoomAction(1.1));
        getActions().addAction(ActionFactory.createPopupMenuAction(new MainPopupMenuProvider()));
        getActions().addAction(this.createSelectAction()); // to invert selection when network is clciked
        getActions().addAction(new KeyboardDeleteAction(this));


        addObjectSceneListener(new ObjectSceneListener() {
            @Override
            public void objectAdded(ObjectSceneEvent event, Object addedObject) {
                //       throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                // on neuron add redraw layer
                // on layer add redraw network/layers
                // on connection add redraw connection between objects
            }

            @Override
            public void objectRemoved(ObjectSceneEvent event, Object removedObject) {
                //     throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void objectStateChanged(ObjectSceneEvent event, Object changedObject, ObjectState previousState, ObjectState newState) {
                //   throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void selectionChanged(ObjectSceneEvent event, Set<Object> previousSelection, Set<Object> newSelection) {
                for (Object o : previousSelection) {
                    content.remove(o);
                }

                for (Object o : newSelection) {
                    content.add(o);
                }
            }

            @Override
            public void highlightingChanged(ObjectSceneEvent event, Set<Object> previousHighlighting, Set<Object> newHighlighting) {
                //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void hoverChanged(ObjectSceneEvent event, Object previousHoveredObject, Object newHoveredObject) {
                //     throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void focusChanged(ObjectSceneEvent event, Object previousFocusedObject, Object newFocusedObject) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        }, ObjectSceneEventType.OBJECT_SELECTION_CHANGED);

        getActions().addAction(ActionFactory.createAcceptAction(new AcceptProvider() {
            @Override
            public ConnectorState isAcceptable(final Widget widget, final Point point, final Transferable t) {

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
//                        DataFlavor flavor = t.getTransferDataFlavors()[3];
//                Class droppedClass = flavor.getRepresentationClass();
                        Image dragImage = getImageFromTransferable(t);
                        JComponent view = widget.getScene().getView();

                        // Graphics2D graphics = (Graphics2D) view.getGraphics(); 
                        Graphics2D graphics = widget.getScene().getGraphics();

                        //Rectangle visRect = view.getVisibleRect();
                        //view.paintImmediately(visRect.x, visRect.y, visRect.width, visRect.height);
//                Image dragImage = null;
//                try {
//                    dragImage = (Image) t.getTransferData(DataFlavor.imageFlavor);
//                } catch (UnsupportedFlavorException ex) {
//                    Exceptions.printStackTrace(ex);
//                } catch (IOException ex) {
//                    Exceptions.printStackTrace(ex);
//                } 

                        graphics.drawImage(dragImage,
                                AffineTransform.getTranslateInstance(point.getLocation().getX(),
                                point.getLocation().getY()),
                                null);
                        // widget.getScene().paint(graphics);
                    }
                });


                DataFlavor dataSetflavor = t.getTransferDataFlavors()[1];
                try {        
                    DataObject dsdo= (DataObject)t.getTransferData(dataSetflavor);
                    DataSet ds = dsdo.getLookup().lookup(DataSet.class);
                    
                    if (ds!=null) {                                             
                        return ConnectorState.ACCEPT;
                    }
                    
                } catch (        UnsupportedFlavorException | IOException ex) {
                    Exceptions.printStackTrace(ex);
                }

                return ConnectorState.REJECT;
            }

            @Override
            public void accept(Widget widget, Point point, Transferable t) {
                DataFlavor dataSetflavor = t.getTransferDataFlavors()[1];
                try {        
                    DataObject dsdo= (DataObject)t.getTransferData(dataSetflavor);
                    DataSet ds = dsdo.getLookup().lookup(DataSet.class);
                    
                        if (ds.getInputSize()!=neuralNetwork.getInputsCount()) {
                            JOptionPane.showMessageDialog(topComponent, "Number of inputs of data set and neural network must be equal!");
                            return;
                        }                    
                    dataSet = ds;                    
                    dataSetLabel.setLabel("DataSet: "+ds.getLabel());
                    topComponent.requestActive();
                } catch (        UnsupportedFlavorException | IOException ex) {
                    Exceptions.printStackTrace(ex);
                }                
            }
        }));

    }

    @Override
    public Lookup getLookup() {
        return aLookup;
    }

    public DataSet getDataSet() {
        return dataSet;
    }
    
    
    
    

    private Image getImageFromTransferable(Transferable transferable) {
        Object o = null;
        try {
            o = transferable.getTransferData(DataFlavor.imageFlavor);
        } catch (IOException | UnsupportedFlavorException ex) {
        }
        return o instanceof Image ? (Image) o : ImageUtilities.loadImage("org/netbeans/shapesample/palette/shape1.png");
    }

    public void setTopComponent(VisualEditorTopComponent topComponent) {
        this.topComponent = topComponent;
    }

    // http://bits.netbeans.org/dev/javadoc/org-netbeans-api-visual/org/netbeans/api/visual/widget/doc-files/documentation.html#ValidationProcess
    public void refresh() {
        visualizeNetwork(); 
        // change focus to visual editor top component
        topComponent.requestActive();
    }

    public void visualizeNetwork() {
        inputsContainerWidget.removeChildren();
        outputsContainerWidget.removeChildren();
        // todo: inputs and outputs container creation along with all possibl escanraios, nice graphics, and text
 
        // clear connections
        connectionLayer.removeChildren();

        createNeuralNetworkWidget();

        if (scenePreferences.isShowConnections()) {
            createConnections();
        }
        this.validate(); // only one call to validate since they ar eusing same scene instance   

        createConnectionsBetweenNeuronsInSameLayer();
    }

    // Creates layer and neuron widgets
    private void createNeuralNetworkWidget() {

        // why do we nned this when we have ObjectScene? - they are still used when drawing connections
        neurons = new ArrayList<>();
        neuronsAndWidgets = new HashMap<>();
        layers = new ArrayList<>();
        layersAndWidgets = new HashMap<>();
        
        neuralNetworkWidget.redrawChildWidgets();        

        // create inputs widgets
        inputsContainerWidget.setLayout(LayoutFactory.createHorizontalFlowLayout(LayoutFactory.SerialAlignment.CENTER, 5));
        outputsContainerWidget.setLayout(LayoutFactory.createHorizontalFlowLayout(LayoutFactory.SerialAlignment.CENTER, 5));

        if (neuralNetwork.getInputNeurons() != null && neuralNetwork.getInputNeurons().length < TOO_MANY_NEURONS) {

            for (int i = 0; i < neuralNetwork.getInputNeurons().length; i++) {
                LabelWidget inputLabel = new LabelWidget(this);
                inputLabel.setLabel("In " + (i + 1));
                inputLabel.setBorder(org.netbeans.api.visual.border.BorderFactory.createRoundedBorder(5, 5, Color.white, Color.black));
                inputsContainerWidget.addChild(inputLabel);

                NeuronWidget targetWidget = neuronsAndWidgets.get(neuralNetwork.getInputNeurons()[i]);

                if (scenePreferences.isShowConnections()) {
                    ConnectionWidget connWidget = new ConnectionWidget(this);

                    connWidget.setTargetAnchorShape(AnchorShape.TRIANGLE_FILLED);
                    connWidget.setSourceAnchor(AnchorFactory.createRectangularAnchor(inputLabel));
                    connWidget.setTargetAnchor(AnchorFactory.createRectangularAnchor(targetWidget));
                    connectionLayer.addChild(connWidget);
                }
            }

            // if first layer has more than 100 neurons, create one  input label and connect it with the first layer
        } else if (neuralNetwork.getInputNeurons() != null) {

            LabelWidget inputLabel = new LabelWidget(this);
            inputLabel.setLabel(neuralNetwork.getInputNeurons().length + " Inputs");
            inputLabel.setBorder(org.netbeans.api.visual.border.BorderFactory.createRoundedBorder(5, 5, Color.white, Color.black));
            inputsContainerWidget.addChild(inputLabel);

            Layer sourceLayer = layers.get(0); // verovatno ne mora da bude samo prvi layer ...

            NeuralLayerWidget sourceWidget = layersAndWidgets.get(sourceLayer);
            if (scenePreferences.isShowConnections()) {
                ConnectionWidget connWidget = new ConnectionWidget(this);
                // create connection between input widget and first neural layer widget
                connWidget.setTargetAnchorShape(AnchorShape.TRIANGLE_FILLED);
                connWidget.setSourceAnchor(AnchorFactory.createRectangularAnchor(inputLabel));
                connWidget.setTargetAnchor(AnchorFactory.createRectangularAnchor(sourceWidget));
                connectionLayer.addChild(connWidget);
            }

            // neuralNetworkWidget.addChild(0, inputsContainerWidget);
        }

        outputsContainerWidget.addChild(new LabelWidget(this, "Outputs:"));
        if (neuralNetwork.getOutputNeurons() != null && neuralNetwork.getOutputNeurons().length < TOO_MANY_NEURONS) {
            for (int i = 0; i < neuralNetwork.getOutputNeurons().length; i++) {
                LabelWidget outputLabel = new LabelWidget(this);
                outputLabel.setLabel("Out " + (i + 1));
                outputLabel.setBorder(org.netbeans.api.visual.border.BorderFactory.createRoundedBorder(5, 5, Color.white, Color.black));
                outputsContainerWidget.addChild(outputLabel);

                NeuronWidget sourceWidget = neuronsAndWidgets.get(neuralNetwork.getOutputNeurons()[i]);
                if (scenePreferences.isShowConnections()) {
                    ConnectionWidget connWidget = new ConnectionWidget(this);

                    connWidget.setTargetAnchorShape(AnchorShape.TRIANGLE_FILLED);
                    connWidget.setSourceAnchor(AnchorFactory.createRectangularAnchor(sourceWidget));
                    connWidget.setTargetAnchor(AnchorFactory.createRectangularAnchor(outputLabel));
                    connectionLayer.addChild(connWidget);
                }
            }
            //if last neural layer  has more than 100 neurons connect that neural layer widget with one output label
        } else if (neuralNetwork.getOutputNeurons() != null) {
            LabelWidget outputLabel = new LabelWidget(this);
            outputLabel.setLabel("Output " + neuralNetwork.getOutputNeurons().length);
            outputLabel.setBorder(org.netbeans.api.visual.border.BorderFactory.createRoundedBorder(5, 5, Color.white, Color.black));
            outputsContainerWidget.addChild(outputLabel);
            Layer targetlayer = layers.get(layers.size() - 1);


            //connect that neural layer widget with the previous one
            NeuralLayerWidget sourceWidget = layersAndWidgets.get(targetlayer);
            if (scenePreferences.isShowConnections()) {
                ConnectionWidget connWidget = new ConnectionWidget(this);

                connWidget.setTargetAnchorShape(AnchorShape.TRIANGLE_FILLED);
                connWidget.setSourceAnchor(AnchorFactory.createRectangularAnchor(sourceWidget));
                connWidget.setTargetAnchor(AnchorFactory.createRectangularAnchor(outputLabel));
                connectionLayer.addChild(connWidget);

            }
        }
        
       
        LearningRule learningRule = neuralNetwork.getLearningRule();
        String learningRuleName = "none";        
        
        if (learningRule != null) { // if learning rule is set
            LearningRuleWidget learningRuleWidget = new LearningRuleWidget(this, learningRule);
            learningRuleName = learningRule.getClass().toString();
            learningRuleName = learningRuleName.substring(learningRuleName.lastIndexOf(".") + 1);

            learningRuleWidget.setLabel(learningRuleName);

            componentsWidgets = new ImageWidget(this);
            componentsWidgets.setLayout(LayoutFactory.createVerticalFlowLayout(LayoutFactory.SerialAlignment.LEFT_TOP, 4));
            componentsWidgets.addChild(learningRuleWidget);
            
         //   removeObject(learningRule); // fix for affertion exception bellow
 //           addObject(learningRule, learningRuleWidget);

  //          neuralNetworkWidget.addChild(componentsWidgets);
   //         componentsWidgets.setPreferredSize(new Dimension(500, 40));
        }
    }
    
    /**
    * Creates single connecting line betweeen two layers
    * Used when there is too many connections between two layers
    */
    private void createNeuralLayersConnection(NeuralLayerWidget sourceLayerWidget, NeuralLayerWidget targetLayerWidget) {
        // create single connection line between two layers
        ConnectionWidget connWidget = new ConnectionWidget(this);
        LabelWidget label = new LabelWidget(this);
        int numOfConnections = sourceLayerWidget.getLayer().getNeuronsCount() * targetLayerWidget.getLayer().getNeuronsCount();
        String numOfConnectionsStr = String.valueOf(numOfConnections);
        label.setLabel(numOfConnectionsStr + " connections");
        connWidget.setTargetAnchorShape(AnchorShape.TRIANGLE_FILLED);
        connWidget.setSourceAnchor(AnchorFactory.createRectangularAnchor(sourceLayerWidget));
        connWidget.setTargetAnchor(AnchorFactory.createRectangularAnchor(targetLayerWidget));
        connWidget.setStroke(new BasicStroke(1, 1, 1, 1, new float[]{5}, 1));
        
        connWidget.addChild(label);
        connWidget.setConstraint(label, LayoutFactory.ConnectionWidgetLayoutAlignment.CENTER, 0.5f);
        connectionLayer.addChild(connWidget);
    }

    private void createConnections() {
        for (int currentLayerIdx = 0; currentLayerIdx < layers.size(); currentLayerIdx++) {
            Layer currentLayer = layers.get(currentLayerIdx);
            if (currentLayer.getNeuronsCount() > TOO_MANY_NEURONS) {

                if (currentLayerIdx == 0) { // first/input layer
                    NeuralLayerWidget sourceLayerWidget = layersAndWidgets.get(currentLayer);
                    NeuralLayerWidget targetLayerWidget = layersAndWidgets.get(layers.get(currentLayerIdx + 1));
                    createNeuralLayersConnection(sourceLayerWidget, targetLayerWidget);
                    // sledeci layer bi trebalo da crta svoj einput konekcije ovo je mozda nepotrebno...

                } else if (currentLayerIdx == layers.size() - 1) { // last, output layer 
                    //(ne mora da znaci da je iz prethodnog lejera
                    NeuralLayerWidget sourceLayerWidget = layersAndWidgets.get(layers.get(currentLayerIdx - 1));
                    NeuralLayerWidget targetLayerWidget = layersAndWidgets.get(layers.get(currentLayerIdx));
                    createNeuralLayersConnection(sourceLayerWidget, targetLayerWidget);

                } else { // hidden layers                      
                    if (!NeuralNetworkUtils.hasInputConnections(currentLayer)) {
                        continue;
                    }

                    NeuralLayerWidget prevLayerWidget = layersAndWidgets.get(layers.get(currentLayerIdx - 1));
                    NeuralLayerWidget currentLayerWidget = layersAndWidgets.get(currentLayer);
                    createNeuralLayersConnection(prevLayerWidget, currentLayerWidget);
                }

            } else {
                // if layer has less than 100 neurons then draw all connections     

                if (currentLayerIdx > 0) { // if not input layer
                    Layer prevLayer = layers.get(currentLayerIdx - 1);
                    if (prevLayer.getNeuronsCount() > TOO_MANY_NEURONS) {
                        if (!NeuralNetworkUtils.hasInputConnections(currentLayer)) {
                            continue;
                        }

                        // draw connection between two layers    
                        NeuralLayerWidget prevLayerWidget = layersAndWidgets.get(layers.get(currentLayerIdx - 1));
                        NeuralLayerWidget currentLayerWidget = layersAndWidgets.get(currentLayer);
                        createNeuralLayersConnection(prevLayerWidget, currentLayerWidget);
                    }
                }
                int numOfConnections = NeuralNetworkUtils.countConnections(layers.get(currentLayerIdx));

                if (numOfConnections <= TOO_MANY_CONNECTIONS) {

                    for (int j = 0; j < layers.get(currentLayerIdx).getNeuronsCount(); j++) {
                        Neuron targetNeuron = layers.get(currentLayerIdx).getNeuronAt(j);
                        Connection[] inputConnections = targetNeuron.getInputConnections();

                        for (int c = 0; c < inputConnections.length; c++) {
                            NeuronWidget targetWidget = neuronsAndWidgets.get(targetNeuron);
                            NeuronWidget sourceWidget = neuronsAndWidgets.get(inputConnections[c].getFromNeuron());
                            if (sourceWidget == null) { // hack when layer is deleted
                                continue;
                            }

                            NeuronConnectionWidget connWidget = createConnectionWidget(inputConnections[c], sourceWidget, targetWidget);
                            connectionLayer.addChild(connWidget);

                            if (getObjects().contains(inputConnections[c])) {
                                removeObject(inputConnections[c]);
                            }
                            addObject(inputConnections[c], connWidget);
                        }
                    }
                } else {
                    NeuralLayerWidget previousLayer = layersAndWidgets.get(layers.get(currentLayerIdx - 1));
                    NeuralLayerWidget nextLayer = layersAndWidgets.get(layers.get(currentLayerIdx));
                    createNeuralLayersConnection(previousLayer, nextLayer);
                }

            }
        }
    }

    public boolean isWaitingLayerClick() {
        return waitingLayerClick;
    }

    public void setWaitingLayerClick(boolean waitingLayerClick) {
        this.waitingLayerClick = waitingLayerClick;
    }

    public NeuralNetworkWidget getNeuralNetworkWidget() {
        return neuralNetworkWidget;
    }

    public LayerWidget getInterractionLayer() {
        return interractionLayer;
    }

    public LayerWidget getConnectionLayer() {
        return connectionLayer;
    }

    public NeuralNetwork getNeuralNetwork() {
        return neuralNetwork;
    }

    public boolean isWaitingClick() {
        return waitingClick;
    }

    public void setWaitingClick(boolean waitingClick) {
        this.waitingClick = waitingClick;
    }

    private void createConnectionsBetweenNeuronsInSameLayer() {
        for (Neuron neuron : neuronsAndWidgets.keySet()) {
            for (ConnectionWidget connWidget : neuronsAndWidgets.get(neuron).getConnections()) {
                Connection connection = ((NeuronConnectionWidget) connWidget).getConnection();
                Neuron fromNeuron = connection.getFromNeuron();
                Neuron toNeuron = connection.getToNeuron();
                if (fromNeuron.getParentLayer().equals(toNeuron.getParentLayer())) {
                    connWidget.setRouter(new RouterConnection(neuronsAndWidgets.get(fromNeuron), neuronsAndWidgets.get(fromNeuron), connWidget.getFirstControlPoint(), connWidget.getLastControlPoint()));
                }
            }
        }
    }

    public NeuralNetworkEditor getNeuralNetworkEditor() {
        return networkEditor;
    }

    private NeuronConnectionWidget createConnectionWidget(Connection connection, NeuronWidget sourceWidget, NeuronWidget targetWidget) {
        NeuronConnectionWidget connWidget = new NeuronConnectionWidget(this, connection, sourceWidget, targetWidget);
        
        connWidget.setTargetAnchorShape(AnchorShape.TRIANGLE_FILLED);
        connWidget.setSourceAnchor(AnchorFactory.createRectangularAnchor(sourceWidget));
        connWidget.setTargetAnchor(AnchorFactory.createRectangularAnchor(targetWidget));
        double connWeight = connection.getWeight().value;
        if (scenePreferences.isShowConnectionWeights()) {
            LabelWidget label = new LabelWidget(this);
            DecimalFormat fourDForm = new DecimalFormat("#.###");
            label.setLabel(fourDForm.format(connWeight));
            connWidget.addChild(label);
            connWidget.setConstraint(label, LayoutFactory.ConnectionWidgetLayoutAlignment.CENTER, 0.5f);
        }
        if (scenePreferences.isWeightHighlighting()) {
            if (connWeight < -0.5) {
                connWidget.setStroke(new BasicStroke((float) 0.5));
            } else if (connWeight >= -0.5 && connWeight < 0.5) {
                connWidget.setStroke(new BasicStroke((float) 1.5));
            } else {
                connWidget.setStroke(new BasicStroke(3));
            }
        }else{
            connWidget.setStroke(new BasicStroke(1));
        }

        sourceWidget.addConnection(connWidget);
        targetWidget.addConnection(connWidget);
        return connWidget;

    }

    public ScenePreferences getScenePreferences() {
        return scenePreferences;
    }

    public void showActivationSize(boolean show) {
        if (scenePreferences.isShowActivationSize() != show) {
            scenePreferences.setShowActivationSize(show);
            refresh();
        }
    }

    public void showActivationColor(boolean show) {
        for (Neuron neuron : neuronsAndWidgets.keySet()) {
            neuronsAndWidgets.get(neuron).setActivationColor(show);
        }
        scenePreferences.setShowActivationColor(show);
    }

    public void showConnections(boolean show) {
        if (scenePreferences.isShowConnections() != show) {
            scenePreferences.setShowConnections(show);
            refresh();
        }
    }

    public void showActivationLevel(boolean show) {
        if (scenePreferences.isShowActivationLevels() != show) {
            scenePreferences.setShowActivationLevels(show);
            refresh();
        }
    }

    public void showConnectionWeights(boolean show) {
        if (scenePreferences.isShowConnectionWeights() != show) {
            scenePreferences.setShowConnectionWeights(show);
            refresh();
        }
    }

    public void weightHighlighting(boolean highlight) {
        if (scenePreferences.isWeightHighlighting() != highlight) {
            scenePreferences.setWeightHighlighting(highlight);
            refresh();
        }
    }

    public HashMap<Neuron, NeuronWidget> getNeuronsAndWidgets() {
        return neuronsAndWidgets;
    }

    public ArrayList<Neuron> getNeurons() {
        return neurons;
    }

    public HashMap<Layer, NeuralLayerWidget> getLayersAndWidgets() {
        return layersAndWidgets;
    }

    public ArrayList<Layer> getLayers() {
        return layers;
    }
    
    
    
    
    
    
}
