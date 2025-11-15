// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.extendedclipboard;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JPopupMenu.Separator;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionListener;

import org.openstreetmap.josm.actions.AbstractPasteAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.gui.datatransfer.PrimitiveTransferable;
import org.openstreetmap.josm.gui.datatransfer.data.PrimitiveTransferData;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetType;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.PreferenceChangedListener;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

public class NodeTemplateListDialog extends ToggleDialog implements DataSelectionListener, ActiveLayerChangeListener {
  private static final String PREF_KEY_NAMES = "NodeTemplateListDialog.nodeTemplates.names";
  private static final String PREF_KEY_KEYS = "NodeTemplateListDialog.nodeTemplates.keys";
  private static final String PREF_KEY_DUAL_LIST = "NodeTemplateListDialog.nodeTemplates.dualList";
  private static final String PREF_KEY_USE_ON_SELECTION = "NodeTemplateListDialog.nodeTemplates.useOnSelection";
  private static final String PREF_KEY_SELECTION_AUTO_OFF = "NodeTemplateListDialog.nodeTemplates.useOnSelectionAutoOff";
  
  private static final String SEPARATOR_NAME = ";;;";
  private static final String SEPARATOR_ICON = "###";
  private static final String SEPARATOR_WAY = "***";
  
  private static final NodeTemplate NODE_TEMPLATE_DUMMY = new NodeTemplate("", null, Collections.emptyMap(), false, false);

  private Rectangle panelBounds;
  private JPanel p;
  private JScrollPane west;
  private JScrollPane east;
  private JSeparator middle;
  private JCheckBox useOnSelection;
  
  private final JList<NodeTemplate> nodeList;
  private final JList<NodeTemplate> nodeList2;
  private DefaultListModel<NodeTemplate> model;
  private DefaultListModel<NodeTemplate> model2;

  private final AddAction add = new AddAction();
  private final EditAction edit = new EditAction();
  private final CopyAction copy = new CopyAction();
  private final PasteAction paste = new PasteAction();
  private final DeleteAction delete = new DeleteAction();
  private final JCheckBoxMenuItem forWays;
  private final JCheckBoxMenuItem notForNodes;

  private final SideButton btnAdd = new SideButton(add, false);
  private final SideButton btnEdit = new SideButton(edit, false);
  private final SideButton btnCopy = new SideButton(copy, false);
  private final SideButton btnPaste = new SideButton(paste, false);
  private final SideButton btnDelete = new SideButton(delete, false);

  private final JPopupMenu popupMenu = new JPopupMenu();
  private final JMenu importMenu = new JMenu(tr("Import node template from preset"));
  private final JMenu setIconMenu = new JMenu(tr("Set icon for selected node template from preset"));
  
  private PreferenceChangedListener prefListener;
  private PreferenceChangedListener prefListener2;
  
  private int timer;
  private Thread autoOffTimer;
  private JLabel autoOffLabel;
  
  private final AbstractAction sortItem = new AbstractAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      if(nodeList != null) {
        NodeTemplate selected = nodeList.getSelectedValue();
        
        if(selected == null) {
          selected = nodeList2.getSelectedValue();
        }
        
        ArrayList<NodeTemplate> nodeTemplateList = new ArrayList<NodeTemplate>();
        
        for(int i = 0; i < model.size(); i++) {
          nodeTemplateList.add(model.get(i));
        }
        
        for(int i = 0; i < model2.size(); i++) {
          nodeTemplateList.add(model2.get(i));
        }
        
        Collections.sort(nodeTemplateList, NodeTemplate.COMPARATOR);
        
        model.removeAllElements();
        model2.removeAllElements();
        
        for(Object el : nodeTemplateList) {
          model.addElement((NodeTemplate)el);
        }
        
        refillLists(true);
        
        if(model.contains(selected)) {
          nodeList.setSelectedValue(selected, true);
        }
        else {
          nodeList2.setSelectedValue(selected, true);
        }
        
        updateBtnEnabledState();
      }
    }
  };  

  public NodeTemplateListDialog() {
    super(tr("Node Template List"), "nodes", tr("Store node tags for recration of nodes with the same tags"),
        Shortcut.registerShortcut("NodeTemplateList.nodetemplatelist", tr("Windows: {0}", tr("Node Template List")),
            KeyEvent.VK_B, Shortcut.ALT_CTRL_SHIFT), 150, true);
    importMenu.setIcon(ImageProvider.get("download", ImageSizes.SMALLICON));
    
    model = new DefaultListModel<>();
    nodeList = new JList<>(model);
    nodeList.setAlignmentX(1.0f);
    
    nodeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    
    model2 = new DefaultListModel<>();
    nodeList2 = new JList<>(model2);
    nodeList2.setAlignmentX(1.0f);
    nodeList2.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    
    forWays = new JCheckBoxMenuItem(tr("Allow usage for ways on selection"), ImageProvider.get(OsmPrimitiveType.WAY));
    forWays.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        NodeTemplate t = nodeList.getSelectedValue();
        
        if(t == null) {
          t = nodeList2.getSelectedValue();
        }
        
        t.setForWays(!t.isForWays());
      }
    });
    
    notForNodes = new JCheckBoxMenuItem(tr("Not for nodes"), ImageProvider.get("not_for_nodes", ImageProvider.ImageSizes.MENU));
    notForNodes.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        NodeTemplate t = nodeList.getSelectedValue();
        
        if(t == null) {
          t = nodeList2.getSelectedValue();
        }
        
        t.setNotForNodes(!t.isNotForNodes());
        
        updateBtnEnabledState();
      }
    });
    
    final DefaultListCellRenderer renderer = new DefaultListCellRenderer() {
      @SuppressWarnings("rawtypes")
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          
          if(value instanceof NodeTemplate) {
            if(((NodeTemplate) value).icon != null) {
              label.setIcon(((NodeTemplate) value).icon);
            }
          }
          
          return label;
      }
    };
    
    nodeList.setCellRenderer(renderer);
    nodeList2.setCellRenderer(renderer);
    
    createPopupMenu();
    
    final MouseAdapter mouseListener = new MouseAdapter() {
      private Thread clickThread;
      private int clickCount;
      
      @Override
      public void mouseReleased(MouseEvent e) {
        handlePopupMenu(e);
      }
      
      @Override
      public void mousePressed(MouseEvent e) {
        handlePopupMenu(e);
      }

      @Override
      public synchronized void mouseClicked(MouseEvent e) {
        updateBtnEnabledState();
        
        clickCount = e.getClickCount();
        
        if(clickThread == null || !clickThread.isAlive()) {
          clickThread = new Thread() {
            public void run() {
              final AtomicInteger knownClickCount = new AtomicInteger(-1);
              
              while(knownClickCount.getAndSet(clickCount) != clickCount) {
                try {
                  Thread.sleep(50);
                } catch (InterruptedException e) {
                  // intentionally ignore
                }
              }
              
              SwingUtilities.invokeLater(() -> {
                if (SwingUtilities.isLeftMouseButton(e) && (knownClickCount.get() >= 1 && knownClickCount.get() <= 3)) {
                  if(knownClickCount.get() == 1 && Config.getPref().getBoolean(PREF_KEY_USE_ON_SELECTION, true)) {
                    if(nodeList.getSelectedIndex() != -1 || nodeList2.getSelectedIndex() != -1) {
                      if(!useOnSelection.isSelected()) {
                        useOnSelection.setSelected(true);
                      }
                      else {
                        checkStartTimer();
                      }
                    }
                  }
                  else if(knownClickCount.get() == 2) {
                    copy.actionPerformed(null);
                    useOnSelection.setSelected(false);
                  }
                  else if(knownClickCount.get() == 3) {
                    paste.actionPerformed(new ActionEvent(btnPaste, 0, "PASTE"));
                    useOnSelection.setSelected(false);
                  }
                  
                  MainApplication.getMainPanel().requestFocusInWindow();
                }
              });
            };
          };
          clickThread.start();
        }
      }
      
      private void handlePopupMenu(MouseEvent e) {
        if(e.getSource() instanceof JList) {
          int index = ((JList<?>)e.getSource()).locationToIndex(e.getPoint());
          
          if(e.isPopupTrigger()) {
            if(index >= 0 && ((JList<?>)e.getSource()).getCellBounds(index, index).contains(e.getPoint())) {
              ((JList<?>)e.getSource()).setSelectedIndex(index);
              
              if(Config.getPref().getBoolean(PREF_KEY_USE_ON_SELECTION, true)) {
                useOnSelection.setEnabled(true);
                useOnSelection.setSelected(true);
              }
            }
            else {
              ((JList<?>)e.getSource()).clearSelection();
            }
            
            updateImportMenu();
            updateBtnEnabledState();
            
            if(nodeList.getSelectedIndex() != -1) {
              forWays.setSelected(nodeList.getSelectedValue().isForWays());
              notForNodes.setSelected(nodeList.getSelectedValue().isNotForNodes());
            }
            else if(nodeList2.getSelectedIndex() != -1) {
              forWays.setSelected(nodeList2.getSelectedValue().isForWays());
              notForNodes.setSelected(nodeList2.getSelectedValue().isNotForNodes());
            }
            
            popupMenu.show(e.getComponent(), e.getPoint().x, e.getPoint().y);
          }
        }
      }
    };
    
    nodeList.addMouseListener(mouseListener);
    nodeList2.addMouseListener(mouseListener);
    
    ListSelectionListener selectionListener = e -> {
      if(!e.getValueIsAdjusting()) {
        if(e.getSource() == nodeList) {
          if(nodeList.getSelectedIndex() != -1) {
            nodeList2.clearSelection();
          }
        }
        else {
          if(nodeList2.getSelectedIndex() != -1) {
            nodeList.clearSelection();
          }
        }
      }
      
      if(nodeList.getSelectedIndex() == -1 && nodeList2.getSelectedIndex() == -1) {
        useOnSelection.setSelected(false);
        useOnSelection.setEnabled(false);
      }
      else {
        useOnSelection.setEnabled(true);
      }
    };
    
    nodeList.addListSelectionListener(selectionListener);
    nodeList2.addListSelectionListener(selectionListener);
    
    useOnSelection = new JCheckBox(tr("Use on selection"));
    useOnSelection.setToolTipText(tr("If activated selected template is automatically used on the selection in the map view, if single object is selected."));
    useOnSelection.setEnabled(false);
    useOnSelection.addItemListener(e -> {
      if(e.getStateChange() == ItemEvent.SELECTED) {
        checkStartTimer();
      }
      
      if((nodeList.getSelectedIndex() == -1 && nodeList2.getSelectedIndex() == -1) || !useOnSelection.isSelected()) {
        timer = 0;
      }
    });
    
    p = new JPanel(new GridBagLayout());
    
    west = new JScrollPane(nodeList);
    west.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    east = new JScrollPane(nodeList2);
    east.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    
    middle = new JSeparator(JSeparator.VERTICAL);
    
    autoOffLabel = new JLabel(tr("off in {0} seconds"));
    autoOffLabel.setVisible(false);
    
    JPanel selection = new JPanel(new BorderLayout(10, 0));
    selection.add(useOnSelection, BorderLayout.CENTER);
    selection.add(autoOffLabel, BorderLayout.EAST);
    useOnSelection.setVisible(Config.getPref().getBoolean(PREF_KEY_USE_ON_SELECTION, true));
    
    prefListener = e -> {
      if(!Boolean.parseBoolean((String)e.getNewValue().getValue())) {
        for(int i = 0; i < model2.size(); i++) {
          model.addElement(model2.get(i));
        }
        
        model2.clear();
      }
      
      panelBounds.setBounds(0, 0, 0, 0);
      refillLists();
    };
    
    prefListener2 = e -> {
      
      if(Boolean.parseBoolean((String)e.getNewValue().getValue())) {
        selection.setVisible(true);
      }
      else {
        selection.setVisible(false);
      }
      
      p.invalidate();
    };
    
    p.add(selection, GBC.std(1,1).span(3).fill(GBC.HORIZONTAL));
    p.add(west, GBC.std(1, 2).fill(GBC.VERTICAL));
    p.add(middle, GBC.std(2, 2).fill(GBC.VERTICAL));
    p.add(east, GBC.std(3, 2).fill(GBC.VERTICAL));
    
    west.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        refillLists();
      }
    });
    
    p.addPropertyChangeListener("ancestor", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getNewValue() != null && (panelBounds == null || !panelBounds.equals(p.getBounds()))) {
          west.setPreferredSize(new Dimension((p.getWidth()-middle.getPreferredSize().width)/2,10));
          east.setPreferredSize(west.getPreferredSize());
        }
      }
    });
        
    createLayout(p, false, Arrays.asList(this.btnAdd, this.btnCopy, this.btnPaste, this.btnEdit, this.btnDelete));
    
    for(int i = 0; i < titleBar.getComponentCount(); i++) {
      if(titleBar.getComponent(i) instanceof JPanel) {
        JButton settings = new JButton(ImageProvider.get("preference", ImageProvider.ImageSizes.SMALLICON));
        settings.setBorder(BorderFactory.createEmptyBorder());
        settings.addActionListener(e -> {
          JPopupMenu menu = new JPopupMenu();
          
          JCheckBoxMenuItem item = new JCheckBoxMenuItem(tr("Dual list"));
          item.setSelected(Config.getPref().getBoolean(PREF_KEY_DUAL_LIST, true));
          item.addActionListener(a -> Config.getPref().putBoolean(PREF_KEY_DUAL_LIST, !Config.getPref().getBoolean(PREF_KEY_DUAL_LIST, true)));
          
          menu.add(item);
          
          item = new JCheckBoxMenuItem(tr("Use on selection"));
          item.setSelected(Config.getPref().getBoolean(PREF_KEY_USE_ON_SELECTION, true));
          item.addActionListener(a -> Config.getPref().putBoolean(PREF_KEY_USE_ON_SELECTION, !Config.getPref().getBoolean(PREF_KEY_USE_ON_SELECTION, true)));
          
          menu.add(item);
          
          JMenu autoOff = new JMenu(tr("Auto off timer for selection"));
          
          int timer = Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10);
          
          ActionListener timerAction = ta -> {
            String value = ((JCheckBoxMenuItem)ta.getSource()).getText();
            
            int toSet = 0;
            
            if(value.matches("\\d+")) {
              toSet = Integer.parseInt(value);
            } 
            
            Config.getPref().putInt(PREF_KEY_SELECTION_AUTO_OFF, toSet);
            
            if(useOnSelection.isSelected()) {
              checkStartTimer();
            }
          };
          
          JCheckBoxMenuItem timerOff = new JCheckBoxMenuItem(tr("disabled"), timer == 0);
          timerOff.addActionListener(timerAction);
          JCheckBoxMenuItem timer5 = new JCheckBoxMenuItem("5", timer == 5);
          timer5.addActionListener(timerAction);
          JCheckBoxMenuItem timer10 = new JCheckBoxMenuItem("10", timer == 10);
          timer10.addActionListener(timerAction);
          JCheckBoxMenuItem timer20 = new JCheckBoxMenuItem("20", timer == 20);
          timer20.addActionListener(timerAction);
          JCheckBoxMenuItem timer30 = new JCheckBoxMenuItem("30", timer == 30);
          timer30.addActionListener(timerAction);
          
          autoOff.add(timerOff);
          autoOff.add(timer5);
          autoOff.add(timer10);
          autoOff.add(timer20);
          autoOff.add(timer30);
          
          menu.add(autoOff);
          
          menu.show((JButton)e.getSource(), ((JButton)e.getSource()).getWidth(), ((JButton)e.getSource()).getHeight());
        });
        
        titleBar.add(settings, i+2);
        
        break;
      }
    }
    
    p.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        int width = (p.getWidth()-middle.getPreferredSize().width)/2;
        
        if(west.getPreferredSize().width != width || panelBounds == null || !panelBounds.equals(p.getBounds())) {
          west.setPreferredSize(new Dimension(width, 10));
          east.setPreferredSize(west.getPreferredSize());
          p.revalidate();
        }
      }
    });
    
    load();
  }
  
  private void checkStartTimer() {
    timer = Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10) * 1000 + 500;
    
    if(timer > 500 && (autoOffTimer == null || !autoOffTimer.isAlive())) {
      autoOffTimer = new Thread() {
        public void run() {
          SwingUtilities.invokeLater(() -> {
            autoOffLabel.setText(tr("off in {0} seconds", String.format("%02d", Math.round(timer/1000))));
            autoOffLabel.setVisible(true);
          });
          
          while((timer-= 200) > 500) {
            try {
              Thread.sleep(200);
            } catch (InterruptedException e) {
              // ignore
            }
            
            SwingUtilities.invokeLater(() -> autoOffLabel.setText(tr("off in {0} seconds", String.format("%02d", Math.round(timer/1000)))));
          }
          
          SwingUtilities.invokeLater(() -> {
            autoOffLabel.setVisible(false);
            
            if(Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10) != 0) {
              useOnSelection.setSelected(false);
            }
          });
        }
      };
      autoOffTimer.start();
    }
  }
  
  private synchronized void refillLists() {
    refillLists(false);
  }
  
  private synchronized void refillLists(boolean force) {
    if(force || panelBounds == null || !panelBounds.equals(p.getBounds())) {
      boolean visible = false;
      
      if(Config.getPref().getBoolean(PREF_KEY_DUAL_LIST, true)) {
        int entryCount = model.size() + model2.size();
        int split = nodeList.getVisibleRowCount();
        
        Rectangle a = nodeList.getVisibleRect();
        
        if(model.size() > 0) {      
          split = a.height/nodeList.getCellBounds(0, 0).height;
        }
        
        if(entryCount > split) {
          DefaultListModel<NodeTemplate> m1 = new DefaultListModel<NodeTemplate>();
          DefaultListModel<NodeTemplate> m2 = new DefaultListModel<NodeTemplate>();
          
          int n = split;
          
          if(entryCount >= split*2) {
            n = entryCount / 2;
            
            if(entryCount % 2 != 0) {
              n++;
            }
          }
            
          for(int i = 0; i < model.size(); i++) {
            if(n > 0) {
              m1.addElement(model.get(i));
              n--;
            }
            else {
              m2.addElement(model.get(i));
            }
          }
          
          for(int i = 0; i < model2.size(); i++) {
            if(n > 0) {
              m1.addElement(model2.get(i));
              n--;
            }
            else {
              m2.addElement(model2.get(i));
            }
          }
          
          model = m1;
          model2 = m2;
          
          nodeList.setModel(model);
          nodeList2.setModel(model2);
          
          west.setPreferredSize(new Dimension((p.getWidth()-middle.getPreferredSize().width)/2,10));
          east.setPreferredSize(west.getPreferredSize());
          visible = true;
        }
      }
      
      middle.setVisible(visible);
      east.setVisible(visible);
      
      if(!visible) {
        if(!model2.isEmpty()) {
          for(int i = 0; i < model2.size(); i++) {
            model.addElement(model2.get(i));
          }
          model2.clear();
        }
        
        west.setPreferredSize(new Dimension(p.getWidth(),10));
      }
      
      panelBounds = p.getBounds();
      useOnSelection.setSelected(false);
    }
  }
  
  private class IconAction extends AbstractAction {
    private String iconName;
    
    private IconAction(JMenuItem preset) {      
      Action parent = preset.getAction();
      iconName = ((TaggingPreset)parent).iconName;
      putValue(NAME, preset.getText());
      putValue(SHORT_DESCRIPTION, parent.getValue(SHORT_DESCRIPTION));
      putValue(SMALL_ICON, parent.getValue(SMALL_ICON));
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      NodeTemplate n = nodeList.getSelectedValue();
      
      if(n == null) {
        n = nodeList2.getSelectedValue();
      }
      
      if(n != null) {
        n.iconName = iconName;
        n.loadIcon();
        nodeList.repaint();
        nodeList2.repaint();;
      }
    }
  }
  
  private class PresetAction extends AbstractAction {
    private Action parent;
    
    private PresetAction(JMenuItem preset) {      
      parent = preset.getAction();
      putValue(NAME, preset.getText());
      putValue(SHORT_DESCRIPTION, parent.getValue(SHORT_DESCRIPTION));
      putValue(SMALL_ICON, parent.getValue(SMALL_ICON));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if(parent != null) {
        int index = nodeList.getSelectedIndex();
        
        model.add(0, NODE_TEMPLATE_DUMMY);
        nodeList.setSelectedIndex(0);
        
        paste.actionPerformed(e);
        
        model.remove(0);
        nodeList.setSelectedIndex(index);
        
        parent.actionPerformed(e);
        
        final Collection<Node> nodeList = OsmDataManager.getInstance().getActiveDataSet().getSelectedNodes();

        for (Node node : nodeList) {
          if(!node.getKeys().isEmpty()) {
            if(model2.isEmpty()) {
              model.addElement(new NodeTemplate(((TaggingPreset)parent).iconName, node));
            }
            else {
              model2.addElement(new NodeTemplate(((TaggingPreset)parent).iconName, node));
            }
          }
        }
        
        OsmDataManager.getInstance().getActiveDataSet().clearSelection();
        
        OsmDataManager.getInstance().getActiveDataSet().update(() -> {
          for (OsmPrimitive osm : nodeList) {
            osm.setDeleted(true);
          }
        });
        
        refillLists();
      }
    }
  }
  
  private JMenu createPresetMenu(JMenu parent, JMenu parent2, JMenu menu) {
    Component[] subMenus = menu.getMenuComponents();
    for(Component subMenu : subMenus) {
      if(subMenu instanceof JMenu) {
        JMenu sub = new JMenu();
        sub.setText(((JMenu) subMenu).getText());
        sub.setIcon(((JMenu) subMenu).getIcon());
        
        JMenu sub2 = new JMenu();
        sub2.setText(((JMenu) subMenu).getText());
        sub2.setIcon(((JMenu) subMenu).getIcon());
        
        createPresetMenu(sub, sub2, (JMenu)subMenu);
        
        if(sub.getMenuComponentCount() > 0) {
          parent.add(sub);
          parent2.add(sub2);
        }
      }
      else if(subMenu instanceof Separator) {
        if(parent.getMenuComponentCount() > 0) {
          parent.addSeparator();
          parent2.addSeparator();
        }
      }
      else if(subMenu instanceof JMenuItem) {
        final Action a = ((JMenuItem) subMenu).getAction();
        
        if(a instanceof TaggingPreset && ((TaggingPreset) a).types != null && ((TaggingPreset) a).types.contains(TaggingPresetType.NODE)) {
          parent.add(new PresetAction((JMenuItem)subMenu));
          parent2.add(new IconAction((JMenuItem)subMenu));
        }
      }
    }
    
    return parent;
  }
  
  private void updateImportMenu() {
    importMenu.removeAll();
    setIconMenu.removeAll();
    
    createPresetMenu(importMenu, setIconMenu, MainApplication.getMenu().presetsMenu);
  }
  
  private void createPopupMenu() {
    sortItem.putValue(Action.NAME, tr("Sort list alphabetically"));
    sortItem.putValue(Action.SMALL_ICON, ImageProvider.get("dialogs", "sort", ImageSizes.SMALLICON));
    
    popupMenu.add(importMenu);
    popupMenu.add(setIconMenu);
    popupMenu.add(sortItem);
    popupMenu.add(forWays);
    popupMenu.add(notForNodes);
    popupMenu.add(copy);
    popupMenu.add(edit);
    popupMenu.add(paste);
    popupMenu.add(delete);
  }
  
  @Override
  public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) { 
    Layer layer = e.getSource().getActiveLayer();
    OsmDataLayer dataLayer = e.getSource().getActiveDataLayer();
    
    nodeList.setEnabled(layer != null && !layer.isBackgroundLayer() && dataLayer != null && !dataLayer.isLocked());
    updateBtnEnabledState();
  }

  @Override
  public void selectionChanged(SelectionChangeEvent event) {
    add.updateEnabledState();
    
    if(useOnSelection.isVisible() && useOnSelection.isSelected()) {
      Set<OsmPrimitive> sel = event.getSelection();
      
      if(sel.size() == 1) {
        timer = Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10) * 1000 + 500;
        
        sel.forEach(p -> {
          NodeTemplate t = nodeList.getSelectedValue();
          
          if(t == null) {
            t = nodeList2.getSelectedValue();
          }
          
          if(t != null) {
            boolean forWays = t.isForWays();
            boolean notForNodes = t.isNotForNodes();
            
            t.map.forEach((key, value) -> {
              ArrayList<Command> cmds = new ArrayList<>();
              
              if(((p instanceof Node && !notForNodes) || (forWays && p instanceof Way)) && !p.hasTag(key, value)) {
                cmds.add(new ChangePropertyCommand(p, key, value));
              }
              
              if(!cmds.isEmpty()) {
                UndoRedoHandler.getInstance().add(new SequenceCommand("add template to selection", cmds));
              }
            });
          }
        });
      }
    }
  }

  private void updateBtnEnabledState() {
    sortItem.setEnabled(model.size() > 1 && nodeList.isEnabled());
    add.updateEnabledState();
    edit.updateEnabledState();
    copy.updateEnabledState();
    paste.updateEnabledState();
    delete.updateEnabledState();
  }

  private void repaintSelectedRow(JList<NodeTemplate> nodeList) {
    int index = nodeList.getSelectedIndex();
    if (index >= 0) {
      nodeList.repaint(nodeList.getCellBounds(index, index));
    }
  }

  @Override
  public void showNotify() {
    SelectionEventManager.getInstance().addSelectionListenerForEdt(this);
    MainApplication.getLayerManager().addActiveLayerChangeListener(this);
    Config.getPref().addKeyPreferenceChangeListener(PREF_KEY_DUAL_LIST, prefListener);
    Config.getPref().addKeyPreferenceChangeListener(PREF_KEY_USE_ON_SELECTION, prefListener2);
    updateBtnEnabledState();
  }

  @Override
  public synchronized void hideNotify() {
    SelectionEventManager.getInstance().removeSelectionListener(this);
    MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
    Config.getPref().removeKeyPreferenceChangeListener(PREF_KEY_DUAL_LIST, prefListener);
    Config.getPref().removeKeyPreferenceChangeListener(PREF_KEY_USE_ON_SELECTION, prefListener2);
  }

  @Override
  public void destroy() {
    save();
    super.destroy();
  }

  private void load() {    
    List<String> names = Config.getPref().getList(PREF_KEY_NAMES, null);
    List<Map<String, String>> keys = Config.getPref().getListOfMaps(PREF_KEY_KEYS, null);
    
    if (names != null && keys != null) {
      int n = Math.min(names.size(), keys.size());

      for (int i = 0; i < n; i++) {
        String name = names.get(i);
        String iconName = null;
        boolean forWays = false;
        boolean notForNodes = false;

        if(name.contains(SEPARATOR_ICON)) {
          iconName = name.substring(name.indexOf(SEPARATOR_ICON)+SEPARATOR_ICON.length());
          name = name.substring(0,name.indexOf(SEPARATOR_ICON));
        }
        
        if(name.contains(SEPARATOR_WAY)) {
          String test = name.substring(0, name.lastIndexOf((SEPARATOR_WAY)));
          
          if(test.contains(SEPARATOR_WAY)) {
            notForNodes = Boolean.parseBoolean(test.substring(0,test.indexOf(SEPARATOR_WAY)));
            test = test.substring(test.indexOf(SEPARATOR_WAY)+SEPARATOR_WAY.length());
          }
          
          forWays = Boolean.parseBoolean(test);
                  
          name = name.substring(name.lastIndexOf(SEPARATOR_WAY)+SEPARATOR_WAY.length());
        }
        
        if (name.contains(SEPARATOR_NAME)) {
          name = name.substring(0, name.indexOf(SEPARATOR_NAME));
        }

        model.addElement(new NodeTemplate(name, iconName, keys.get(i), forWays, notForNodes));
      }
    }
    else {
      final String[] nameArr = {tr("Tree"),tr("Waste Basket"),tr("Bench")};
      final String[] iconArr = {"presets/landmark/trees_broad_leaved.svg", "presets/service/recycling/waste_basket.svg", "presets/leisure/bench.svg"};
      final Tag[] tags = {new Tag("natural", "tree"), new Tag("amenity", "waste_basket"), new Tag("amenity", "bench")};
      
      for(int i = 0; i < tags.length; i++) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(tags[i].getKey(), tags[i].getValue());
        
        model.addElement(new NodeTemplate(nameArr[i], iconArr[i], map, false, false));
      }
      
      sortItem.actionPerformed(null);
    }
  }

  private void saveModel(DefaultListModel<NodeTemplate> model, ArrayList<Map<String, String>> keysList, ArrayList<String> namesList) {
    for (int i = 0; i < model.size(); i++) {
      NodeTemplate template = model.get(i);

      String name = template.name;
      int count = 1;

      while (namesList.contains(name)) {
        name = template.name + SEPARATOR_NAME + count++;
      }

      if(template.isNotForNodes()) {
        name = "true" + SEPARATOR_WAY + name;
      }
      
      if(template.isForWays()) {
        name = "true" + SEPARATOR_WAY + name;
      }
      
      if(template.iconName != null) {
        name += SEPARATOR_ICON + template.iconName;
      }
      
      namesList.add(name);

      HashMap<String, String> keysMap = new HashMap<>();

      template.map.forEach((key, value) -> {
        keysMap.put(key, value);
      });

      keysList.add(keysMap);
    }
  }
  
  @Override
  public void collapse() {
    super.collapse();
    
    revalidate();
  }
  
  private void save() {
    final ArrayList<Map<String, String>> keysList = new ArrayList<>();
    final ArrayList<String> namesList = new ArrayList<>();

    saveModel(model, keysList, namesList);
    saveModel(model2, keysList, namesList);

    Config.getPref().putList(PREF_KEY_NAMES, namesList);
    Config.getPref().putListOfMaps(PREF_KEY_KEYS, keysList);
  }
  
  @Override
  public String helpTopic() {
    return "Plugin/ExtendedClipboard";
  }
  
  class AddAction extends JosmAction {
    AtomicBoolean isPerforming = new AtomicBoolean(false);

    AddAction() {
      super( tr("Create new node templates from selected nodes"), /* ICON() */ "dialogs/add", tr("Create new node templates from selected nodes"),
          null,
          false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (!/* successful */isPerforming.compareAndSet(false, true)) {
        return;
      }
      try {
        final Collection<Node> nodeList = OsmDataManager.getInstance().getActiveDataSet().getSelectedNodes();

        for (Node node : nodeList) {
          if(!node.getKeys().isEmpty()) {
            if(model2.isEmpty()) {
              model.addElement(new NodeTemplate(node));
            }
            else {
              model2.addElement(new NodeTemplate(node));
            }
          }
        }
      } finally {
        refillLists();
        updateBtnEnabledState();
        isPerforming.set(false);
      }
    }

    @Override
    protected final void updateEnabledState() {
      DataSet ds = OsmDataManager.getInstance().getActiveDataSet();
      setEnabled(ds != null && !ds.isLocked() && !Utils.isEmpty(OsmDataManager.getInstance().getInProgressSelection())
          && !ds.getSelectedNodes().isEmpty() && nodeList.isEnabled());
    }
  }
  
  class EditAction extends JosmAction {
    EditAction() {
      super(tr("Edit name of selected node template"), /* ICON() */ "dialogs/edit", tr("Edit name of selected node template"), /* Shortcut */ null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      NodeTemplate entry = nodeList.getSelectedValue();
      
      if(entry == null) {
        entry = nodeList2.getSelectedValue();
      }
      
      String result = JOptionPane.showInputDialog(MainApplication.getMainFrame(), tr("Name:"), entry.toString());

      if (result != null && !result.isBlank()) {
        entry.name = result;
        
        if(nodeList.getSelectedValue() != null) {
          repaintSelectedRow(nodeList);
        }
        else {
          repaintSelectedRow(nodeList2);
        }
      }
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled((nodeList != null && nodeList.getSelectedIndex() >= 0 && nodeList.isEnabled())
          || (nodeList2 != null && nodeList2.getSelectedIndex() >= 0 && nodeList2.isEnabled()));
    }
  }

  class CopyAction extends JosmAction {
    CopyAction() {
      super(tr("Copy new node from selected template to JOSM clipboard"), /* ICON() */ "copy", tr("Copy new node from selected template to JOSM clipboard"),
          /* Shortcut */ null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if(isEnabled()) { 
        NodeTemplate template = nodeList.getSelectedValue();
        
        if(template == null) {
          template = nodeList2.getSelectedValue();
        }
        
        if(template != null) {
          ClipboardUtils.copy(new PrimitiveTransferable(PrimitiveTransferData.getDataWithReferences(Collections.singleton(template.createNode()))));
        }
      }
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled((nodeList != null && nodeList.getSelectedValue() != null && !nodeList.getSelectedValue().isNotForNodes() && nodeList.isEnabled() )
          || (nodeList2 != null && nodeList2.getSelectedValue() != null && !nodeList2.getSelectedValue().isNotForNodes() && nodeList2.isEnabled()));
    }
  }

  class PasteAction extends AbstractPasteAction {
    PasteAction() {
      super(tr("Create new node from selected template and past it directly into the map view"), /* ICON() */ "paste", tr("Create new node from selected template and past it directly into the map view"), /* Shortcut */ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      if(isEnabled()) {
        JList<NodeTemplate> list = nodeList;
        
        if(nodeList2.getSelectedValue() != null) {
          list = nodeList2;
        }
        
        if(list.getSelectedValue() != null) {
          transferHandler.pasteOn(getLayerManager().getEditLayer(), MainApplication.getMap().mapView.getCenter(), new PrimitiveTransferable(PrimitiveTransferData.getDataWithReferences(Collections.singleton(list.getSelectedValue().createNode()))));
        }
      }
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled((nodeList != null && nodeList.getSelectedValue() != null && !nodeList.getSelectedValue().isNotForNodes() && nodeList.isEnabled() )
          || (nodeList2 != null && nodeList2.getSelectedValue() != null && !nodeList2.getSelectedValue().isNotForNodes() && nodeList2.isEnabled()));
    }
  }
  
  class DeleteAction extends JosmAction {
    DeleteAction() {
      super(tr("Delete selected node template"), /* ICON() */ "dialogs/delete", tr("Delete selected node template"), /* Shortcut */ null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      JList<NodeTemplate> nodeList = NodeTemplateListDialog.this.nodeList;
      DefaultListModel<NodeTemplate> model = NodeTemplateListDialog.this.model;
      int index = nodeList.getSelectedIndex();
      
      if(index == -1) {
        nodeList = NodeTemplateListDialog.this.nodeList2;
        model = NodeTemplateListDialog.this.model2;
        index = nodeList2.getSelectedIndex();
      }
      
      model.remove(index);
      
      if(model.size()>=index) {
        nodeList.setSelectedIndex(index-1);
      }
      
      refillLists();
      updateBtnEnabledState();
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled((nodeList != null && nodeList.getSelectedIndex() >= 0 && nodeList.isEnabled())
          || (nodeList2 != null && nodeList2.getSelectedIndex() >= 0 && nodeList2.isEnabled()));
    }
  }

  private static final class NodeTemplate {
    private static final Comparator<Object> COMPARATOR = new Comparator<Object>() {
      @Override
      public int compare(Object n0, Object n1) {
        return ((NodeTemplate)n0).name.compareToIgnoreCase(((NodeTemplate)n1).name);
      }
    };
    
    private String name;
    private String iconName;
    private Icon icon;
    private boolean forWays;
    private boolean notForNodes;
    
    private Map<String, String> map;
    
    public NodeTemplate(String name, String iconName, Map<String, String> map, boolean forWays, boolean notForNodes) {
      this.name = name;
      this.map = map;
      this.iconName = iconName;
      this.forWays = forWays;
      this.notForNodes = notForNodes;
      loadIcon();
    }

    public NodeTemplate(String iconName, Node node) {
      map = new LinkedHashMap<>();
      
      node.getKeys().forEach((key,value) -> {
        if(!value.isBlank()) {
          map.put(key, value);
        }
      });
      
      this.iconName = iconName;
      loadName(node);
      loadIcon();
    }
    
    public NodeTemplate(Node node) {
      map = node.getKeys();
      loadName(node);
    }
    
    public void setNotForNodes(boolean notForNodes) {
      this.notForNodes = notForNodes;
    }
    
    public boolean isNotForNodes() {
      return notForNodes;
    }
    
    public void setForWays(boolean value) {
      forWays = value;
    }
    
    public boolean isForWays() {
      return forWays;
    }
    
    private void loadIcon() {
      if(iconName != null) {
        try {
          final TaggingPreset dummy = new TaggingPreset();
          dummy.setIcon(iconName);
          
          Thread wait = new Thread() {
            @Override
            public void run() {
              int time = 1000;
              
              while(dummy.getIcon() == null && (time-=100) > 0) {
                try {
                  sleep(100);
                } catch (InterruptedException e) {
                  // ignore intentionally
                }
              }
              final ImageIcon i = dummy.getIcon();
              
              if(i != null) {
                icon = new ImageIcon() {
                  @Override
                  public int getIconWidth() {
                    return 16;
                  }
                  
                  @Override
                  public int getIconHeight() {
                    return 16;
                  }
                  
                  @Override
                  public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
                    x += getIconWidth()/2 - i.getIconWidth()/2;
                    y += getIconHeight()/2 - i.getIconHeight()/2;
                    
                    i.paintIcon(c, g, x, y);
                  }
                };
              }
            }
          };
          wait.start();
        }catch(Exception e2) {}
      }
    }
    
    private void loadName(Node node) {
      name = node.getLocalName();

      if (name == null) {
        name = "";
        
        map.forEach((key,value) -> {
          if(name.length() > 0) {
            name += ", ";
          }
          
          name += key+"="+value;          
        });
      }
    }

    public Node createNode() {
      Node node = new Node(new EastNorth(0, 0));
      node.setKeys(map);

      return node;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
