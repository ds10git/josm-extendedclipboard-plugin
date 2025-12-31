// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.extendedclipboard.nodetemplatelist;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.color.ColorSpace;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.openstreetmap.josm.actions.AbstractPasteAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SelectCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListener;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager.FireMode;
import org.openstreetmap.josm.data.osm.event.NodeMovedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesAddedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesRemovedEvent;
import org.openstreetmap.josm.data.osm.event.RelationMembersChangedEvent;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.data.osm.event.TagsChangedEvent;
import org.openstreetmap.josm.data.osm.event.WayNodesChangedEvent;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetType;
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.PreferenceChangedListener;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.Shortcut;

public class NodeTemplateListDialog extends ToggleDialog implements DataSelectionListener, ActiveLayerChangeListener {
  private static final int MAXIMUM_LIST_COLUMN_NUMBER = 7;
  private static final int MAX_LIST_COLUMNS_NUMBER_DEFAULT = 3;
  
  private static final String PREF_KEY_MAX_NUMBER_OF_LIST_COLUMNS = "NodeTemplateListDialog.nodeTemplates.maxNumberOfListColumns";
  private static final String PREF_KEY_AUTO_TAG_SELECTION = "NodeTemplateListDialog.nodeTemplates.autoTagSelection";
  private static final String PREF_KEY_AUTO_TAG_CLEAR_SELECTION_AFTER_TAGGING = "NodeTemplateListDialog.nodeTemplates.autoTagClearSelectionAfterTagging";
  private static final String PREF_KEY_AUTO_TAG_SELECTION_AUTO_ACTIVATE = "NodeTemplateListDialog.nodeTemplates.autoTagSelectionAutoEnable";
  private static final String PREF_KEY_AUTO_TAG_SELECTION_AUTO_DEACTIVATE = "NodeTemplateListDialog.nodeTemplates.autoTagSelectionAutoDisable";
  public static final String PREF_KEY_TAG_SELECTION = "NodeTemplateListDialog.nodeTemplates.tagSelection";
  private static final String PREF_KEY_SELECTION_AUTO_OFF = "NodeTemplateListDialog.nodeTemplates.useOnSelectionAutoOff";
  
  private Rectangle panelBounds;
  private JPanel p;
  
  private JCheckBox autoTagSelection;
  
  private final JList<NodeTemplate> nodeList;
  private DefaultListModel<NodeTemplate> model;
  
  private final LinkedList<JList<NodeTemplate>> nodeLists = new LinkedList<>();
  private final LinkedList<DefaultListModel<NodeTemplate>> models = new LinkedList<>();

  private final AddAction add;
  private final EditAction edit;
  private final CopyAction copy;
  private final PasteAction paste;
  private final DeleteAction delete;
  private final JCheckBoxMenuItem forWays;
  private final JCheckBoxMenuItem forClosedWays;
  private final JCheckBoxMenuItem notForNodes;
  private final JCheckBoxMenuItem onlyForUntaggedObjects;

  private final SideButton btnAdd;
  private final SideButton btnEdit;
  private final SideButton btnCopy;
  private final SideButton btnPaste;
  private final SideButton btnDelete;

  private final JPopupMenu popupMenu;
  
  private final JMenu importMenu;
  private final JMenu setIconMenu;
  private final JMenuItem addSeparator;
  private final JMenuItem sortManually;
  
  private PreferenceChangedListener prefListener;
  private PreferenceChangedListener prefListener2;
  private PreferenceChangedListener prefListener3;
  private PreferenceChangedListener prefListener4;
  private PreferenceChangedListener prefListener5;
  
  private boolean wayTaggingPossible;
  private boolean deactivateAutoTaggingIfNotCompatible;
  private boolean clearSelectionAfterApplyingTag;
  
  private int timer;
  private Thread autoOffTimer;
  private JLabel autoOffLabel;
  private boolean noAutoOffTimer;
  private KeyPressReleaseListener keyListener;
  private boolean keyListenerAdded;
  private ActionListener autoTagActionListener;
  private DataSetListener dataSetListener;
  
  private DataSetListener dataSetEnabledStateListener;
  
  private boolean autoTagDisabled;
  
  private JPopupMenu prefMenu;
  private boolean ctrl;
  private boolean shift;
  private SortDialog sortDialog;
  
  private static int HEIGHT_SEPARATOR;
  private JMenuItem deleteItem;
  
  private final AbstractAction sortItem = new AbstractAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      if(nodeList != null) {
        NodeTemplate selected = getSelectedTemplate();
        
        LinkedList<NodeTemplate> nodeTemplateList = new LinkedList<NodeTemplate>();
        
        for(DefaultListModel<NodeTemplate> m : models) {
          for(int i = 0; i < m.size(); i++) {
            if(m.get(i) != NodeTemplateList.SEPARATOR) {
              nodeTemplateList.add(m.get(i));
            }
          }
          
          m.removeAllElements();
        }
        
        Collections.sort(nodeTemplateList, NodeTemplate.COMPARATOR);
        
        NodeTemplateList.get().setList(nodeTemplateList);
        
        for(NodeTemplate t : nodeTemplateList) {
          model.addElement(t);
        }
        
        refillLists(true);
        
        setSelectedNodeTemplate(selected);
        
        updateBtnEnabledState();
      }
    }
  };
  
  private void addColumn(DefaultListCellRenderer renderer) {
    DefaultListModel<NodeTemplate> model2 = new DefaultListModel<>();
    models.add(model2);
    
    JList<NodeTemplate >nodeList2 = createJListWithoutMouseListeners(model2);
    nodeList2.setAlignmentX(1.0f);
    nodeList2.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    nodeList2.setCellRenderer(renderer);
    
    nodeLists.add(nodeList2);
  }
  
  private void sortManually() {
    sortDialog = new SortDialog(this);
    
    updateBtnEnabledState();
    updateAutoTagEnabledState();
    
    Point p = this.p.getLocationOnScreen();
    sortDialog.showDialog(new Rectangle(p.x, p.y, getWidth(), getHeight()));
  }
  
  public void stopSortingManually() {
    sortDialog = null;
    
    int toRemove = -2;
    
    for(int i = 0; i < model.getSize(); i++) {
      if((toRemove == -2 || toRemove == -1) && model.get(i) == NodeTemplateList.SEPARATOR) {
        toRemove = -1;
      }
      else if(toRemove == -1) {
        toRemove = i-1;
        break;
      }
      else {
        break;
      }
    }
    
    for(int i = toRemove; i >= 0; i--) {
      model.remove(i);
    }
    
    for(int i = 1; i < nodeLists.size(); i++) {
      if(nodeLists.get(i).isVisible() && (i == nodeLists.size()-1 || !nodeLists.get(i+1).isVisible()) &&
          models.get(i).get(models.get(i).size()-1) == NodeTemplateList.SEPARATOR) {
        toRemove = -1;
        break;
      }
    }

    updateAutoTagEnabledState();
    updateBtnEnabledState();
    
    if(toRemove != -2) {
      refillLists(true);
    }
  }
  
  public void sortToTop() {
    for(int i = 0; i < nodeLists.size(); i++) {
      JList<NodeTemplate> list = nodeLists.get(i);
      
      int index = list.getSelectedIndex();
      
      if(index != -1) {
        NodeTemplate t = models.get(i).remove(index);
        
        for(int k = 0; k < i; k++) {
          models.get(k).add(0, t);
          t = models.get(k).remove(models.get(k).getSize()-1);
        }
        
        models.get(i).add(0, t);
        
        nodeList.setSelectedIndex(0);
        nodeList.ensureIndexIsVisible(0);
        break;
      }
    }
  }
  
  public void sortToTopOfList() {
    for(int i = 0; i < nodeLists.size(); i++) {
      JList<NodeTemplate> list = nodeLists.get(i);
      
      int index = list.getSelectedIndex();
      
      if(index != -1) {
        NodeTemplate t = models.get(i).remove(index);
        models.get(i).add(0, t);
        
        list.setSelectedIndex(0);
        list.ensureIndexIsVisible(0);
        break;
      }
    }
  }
  
  public void sortUp() {
    for(int i = 0; i < nodeLists.size(); i++) {
      JList<NodeTemplate> list = nodeLists.get(i);
      
      int index = list.getSelectedIndex();
      
      if(index != -1 && (list != nodeList || index > 0)) {
        if(index > 0) {
          NodeTemplate t = models.get(i).remove(index);
          models.get(i).add(index-1, t);
          
          list.setSelectedIndex(index-1);
          list.ensureIndexIsVisible(index-1);
        }
        else {
          NodeTemplate t = models.get(i).remove(index);
          NodeTemplate other = models.get(i-1).remove(models.get(i-1).size()-1);
          
          models.get(i-1).addElement(t);
          models.get(i).add(index, other);
          list.clearSelection();
          nodeLists.get(i-1).setSelectedIndex(models.get(i-1).size()-1);
          nodeLists.get(i-1).ensureIndexIsVisible(models.get(i-1).size()-1);
        }
        
        break;
      }
    }
  }
  
  public void sortDown() {
    for(int i = 0; i < nodeLists.size(); i++) {
      JList<NodeTemplate> list = nodeLists.get(i);
      
      int index = list.getSelectedIndex();
      
      if(index != -1 && (index < list.getModel().getSize()-1 || i < nodeLists.size()-1 && nodeLists.get(i+1).isVisible())) {
        if(index < list.getModel().getSize()-1) {
          NodeTemplate t = models.get(i).remove(index);
          models.get(i).add(index+1, t);
          
          list.setSelectedIndex(index+1);
          list.ensureIndexIsVisible(index+1);
        }
        else if(nodeLists.size()-2 > i && nodeLists.get(i+1).isVisible()) {
          NodeTemplate t = models.get(i).remove(index);
          NodeTemplate other = models.get(i+1).remove(0);
          
          models.get(i+1).add(0, t);
          models.get(i).addElement(other);
          list.clearSelection();
          nodeLists.get(i+1).setSelectedIndex(0);
          nodeLists.get(i+1).ensureIndexIsVisible(0);
        }
        
        break;
      }
    }
  }
  
  public void sortToBottomOfList() {
    for(int i = 0; i < nodeLists.size(); i++) {
      JList<NodeTemplate> list = nodeLists.get(i);
      
      int index = list.getSelectedIndex();
      
      if(index != -1) {
        NodeTemplate t = models.get(i).remove(index);
        models.get(i).addElement(t);
        
        list.setSelectedIndex(models.get(i).size()-1);
        list.ensureIndexIsVisible(models.get(i).size()-1);
        break;
      }
    }
  }
  
  public void sortToBottom() {
    for(int i = 0; i < nodeLists.size(); i++) {
      JList<NodeTemplate> list = nodeLists.get(i);
      
      int index = list.getSelectedIndex();
      
      if(index != -1) {
        NodeTemplate t = models.get(i).remove(index);
        
        int insertIndex = i;
        
        for(int k = i+1; k < nodeLists.size(); k++) {
          if(nodeLists.get(k).isVisible()) {
            NodeTemplate temp = models.get(k).remove(0);
            models.get(k-1).addElement(temp);
            
            insertIndex = k;
          }
          else {
            break;
          }
        }
        
        models.get(insertIndex).addElement(t);
        nodeLists.get(insertIndex).setSelectedIndex(models.get(insertIndex).size()-1);
        nodeLists.get(insertIndex).ensureIndexIsVisible(models.get(insertIndex).size()-1);
        
        break;
      }
    }
  }
  
  private void addSeparator() {
    for(JList<NodeTemplate> list : nodeLists) {
      int index = list.getSelectedIndex();
      
      if(index != -1 && (list != nodeList || index > 0)) {
        ((DefaultListModel<NodeTemplate>)list.getModel()).add(index, NodeTemplateList.SEPARATOR);
        refillLists(true);
        break;
      }
    }
  }
  
  private JList<NodeTemplate> createJListWithoutMouseListeners(DefaultListModel<NodeTemplate> model) {
    JList<NodeTemplate> list = new JList<>(model);
    MouseListener[] listeners = list.getMouseListeners();
    
    for(MouseListener l : listeners) {
      list.removeMouseListener(l);
    }
    
    return list;
  }
  
  public NodeTemplateListDialog() {
    super(tr("Node Template List"), "nodes", tr("Store node tags for recration of nodes with the same tags"),
        Shortcut.registerShortcut("NodeTemplateList.nodetemplatelist", tr("Windows: {0}", tr("Node Template List")),
            KeyEvent.VK_N, Shortcut.ALT_CTRL_SHIFT), 150, true);
    
    final DefaultListCellRenderer renderer = new DefaultListCellRenderer() {
      private JPanel createSeparator() {
        JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
        
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(Box.createRigidArea(new Dimension(0, 2)));
        p.add(sep);
        p.add(Box.createRigidArea(new Dimension(0, 2)));
        
        HEIGHT_SEPARATOR = p.getPreferredSize().height;
        
        return p;
      }
      
      private final JPanel SEPARATOR = createSeparator();
      @SuppressWarnings("rawtypes")
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          
          if(value == NodeTemplateList.SEPARATOR) {
            return SEPARATOR;
          }
          else if(value instanceof NodeTemplate) {
            NodeTemplate t = (NodeTemplate)value;
            
            if(t.getIcon() != null) {
              label.setIcon(t.getIcon());
            }
            else {
              label.setIcon(null);
            }
            
            label.setEnabled(t.isEnabled(wayTaggingPossible));
          }
          
          return label;
      }
    };
    
    wayTaggingPossible = Config.getPref().getBoolean(PREF_KEY_TAG_SELECTION, true) ||Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION, true);
    deactivateAutoTaggingIfNotCompatible = Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_DEACTIVATE, false);
    clearSelectionAfterApplyingTag = Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_CLEAR_SELECTION_AFTER_TAGGING, true);
    
    popupMenu = new JPopupMenu();
    importMenu = new JMenu(tr("Import node template from preset"));
    importMenu.setIcon(ImageProvider.get("download", ImageSizes.MENU));
    setIconMenu = new JMenu(tr("Set icon for selected node template from preset"));
    setIconMenu.setIcon(ImageProvider.get("imagery_menu", ImageProvider.ImageSizes.MENU));
    addSeparator = new JMenuItem(tr("Insert separator"));
    addSeparator.setIcon(ImageProvider.get("hseparator", ImageProvider.ImageSizes.MENU));
    addSeparator.addActionListener(e -> {
      addSeparator();
    });
    
    sortManually = new JMenuItem(tr("Sort manually"), ImageProvider.get("dialogs/reverse", ImageSizes.MENU));
    sortManually.addActionListener(e -> {
      sortManually();
    });
    
    model = new DefaultListModel<>();
    models.add(model);
    
    nodeList = createJListWithoutMouseListeners(model);
    nodeList.setAlignmentX(1.0f);
    nodeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    nodeList.setCellRenderer(renderer);
    
    nodeLists.add(nodeList);
    
    for(int i = 1; i < MAXIMUM_LIST_COLUMN_NUMBER; i++) {
      addColumn(renderer);
    }
    
    add = new AddAction();
    edit = new EditAction();
    copy = new CopyAction();
    paste = new PasteAction();
    delete = new DeleteAction();
    
    btnAdd = new SideButton(add, false);
    btnEdit = new SideButton(edit, false);
    btnCopy = new SideButton(copy, false);
    btnPaste = new SideButton(paste, false);
    btnDelete = new SideButton(delete, false);
    
    final ImageIcon checked = ImageProvider.get("check_selected", ImageProvider.ImageSizes.SMALLICON);
    
    forWays = new JCheckBoxMenuItem(tr("Allow usage for unclosed ways on selection"));
    CheckBoxMenuIcon.createIcon(forWays, ImageProvider.get(OsmPrimitiveType.WAY), checked, false);
    CheckBoxMenuIcon.createIcon(forWays, ImageProvider.get(OsmPrimitiveType.WAY), checked, true);
    forWays.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        NodeTemplate t = getSelectedTemplate();
        
        t.setForWays(!t.isForWays());
        
        repaintSelectedRow();
      }
    });
    
    forClosedWays = new JCheckBoxMenuItem(tr("Allow usage for closed ways on selection"));
    CheckBoxMenuIcon.createIcon(forClosedWays, ImageProvider.get(OsmPrimitiveType.CLOSEDWAY), checked, false);
    CheckBoxMenuIcon.createIcon(forClosedWays, ImageProvider.get(OsmPrimitiveType.CLOSEDWAY), checked, true);
    forClosedWays.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        NodeTemplate t = getSelectedTemplate();
        
        t.setForClosedWays(!t.isForClosedWays());
        
        repaintSelectedRow();
      }
    });
    
    notForNodes = new JCheckBoxMenuItem(tr("Not for nodes"));
    CheckBoxMenuIcon.createIcon(notForNodes, ImageProvider.get("not_for_nodes", ImageProvider.ImageSizes.MENU), checked, false);
    CheckBoxMenuIcon.createIcon(notForNodes, ImageProvider.get("not_for_nodes", ImageProvider.ImageSizes.MENU), checked, true);
    notForNodes.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        NodeTemplate t = getSelectedTemplate();
        
        t.setNotForNodes(!t.isNotForNodes());
        
        updateBtnEnabledState();
        repaintSelectedRow();
      }
    });
    
    onlyForUntaggedObjects = new JCheckBoxMenuItem(tr("Only for untagged objects"));
    CheckBoxMenuIcon.createIcon(onlyForUntaggedObjects, ImageProvider.get("presets/misc/no_icon", ImageProvider.ImageSizes.MENU), checked, false);
    CheckBoxMenuIcon.createIcon(onlyForUntaggedObjects, ImageProvider.get("presets/misc/no_icon", ImageProvider.ImageSizes.MENU), checked, true);
    onlyForUntaggedObjects.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        NodeTemplate t = getSelectedTemplate();
        
        t.setOnlyForUntaggedObjects(!t.isOnlyForUntaggedObjects());
        
        updateBtnEnabledState();
        repaintSelectedRow();
      }
    });
    
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
        clickCount = e.getClickCount();
        
        JList<?> list = (JList<?>)e.getSource();
          
        int index = list.locationToIndex(e.getPoint());
        
        if(index != -1 && list.getCellBounds(index, index).contains(e.getPoint())) {
          list.setSelectedIndex(index);
        }
        else {
          clearNodeTemplateSelection(null);
        }
        
        updateBtnEnabledState();
        
        if(sortDialog == null && (clickThread == null || !clickThread.isAlive())) {
          clickThread = new Thread() {
            public void run() {
              final AtomicInteger knownClickCount = new AtomicInteger(-1);
              
              while(knownClickCount.getAndSet(clickCount) != clickCount) {
                try {
                  Thread.sleep(200);
                } catch (InterruptedException e) {
                  // intentionally ignore
                }
              }
              
              SwingUtilities.invokeLater(() -> {
                if (SwingUtilities.isLeftMouseButton(e) && (knownClickCount.get() >= 1 && knownClickCount.get() <= 5)) {
                  int modifiers = 0;
                  
                  if(((e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) == KeyEvent.SHIFT_DOWN_MASK)) {
                    modifiers |= ActionEvent.SHIFT_MASK;
                  }
                  if(((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) == KeyEvent.CTRL_DOWN_MASK)) {
                    modifiers |= ActionEvent.CTRL_MASK;
                  }
                  
                  if((knownClickCount.get() == 1 || knownClickCount.get() == 4 || knownClickCount.get() == 5) && Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION, true)) {
                    NodeTemplate t = getSelectedTemplate();
                    
                    if(t != null && t.isEnabled(true)) {
                      noAutoOffTimer = knownClickCount.get() == 5 || (((e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) == KeyEvent.ALT_DOWN_MASK) && ((e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) == KeyEvent.SHIFT_DOWN_MASK));
                      
                      handleSelection(MainApplication.getLayerManager().getEditDataSet().getSelected(), Config.getPref().getBoolean(PREF_KEY_TAG_SELECTION, true) && !noAutoOffTimer, (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) == MouseEvent.CTRL_DOWN_MASK, (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) == MouseEvent.SHIFT_DOWN_MASK, false);
                      
                      if(knownClickCount.get() == 4 || knownClickCount.get() == 5 || (e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) == KeyEvent.ALT_DOWN_MASK || Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_ACTIVATE, true)) {
                        if(!autoTagSelection.isSelected()) {
                          setAutoTagSelectionSelected(true);
                        }
                        else {
                          checkStartTimer();
                        }
                      }
                    }
                  }
                  else if(knownClickCount.get() == 2) {
                    copy.actionPerformed(new ActionEvent(e.getSource(), 0, "COPY", modifiers));
                    setAutoTagSelectionSelected(false);
                  }
                  else if(knownClickCount.get() == 3) {
                    paste.actionPerformed(new ActionEvent(btnPaste, 0, "PASTE", modifiers));
                    setAutoTagSelectionSelected(false);
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
        if(sortDialog == null && e.getSource() instanceof JList) {
          int index = ((JList<?>)e.getSource()).locationToIndex(e.getPoint());
          
          if(e.isPopupTrigger()) {
            if(index >= 0 && ((JList<?>)e.getSource()).getCellBounds(index, index).contains(e.getPoint())) {
              ((JList<?>)e.getSource()).setSelectedIndex(index);
              
              if(Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_ACTIVATE, true)) {
                setAutoTagSelectionSelected(updateAutoTagEnabledState());
              }
            }
            else {
              ((JList<?>)e.getSource()).clearSelection();
            }
            
            updateImportMenu();
            updateBtnEnabledState();
            
            sortItem.setEnabled(((JList<?>)e.getSource()).getModel().getSize() > 0);
            forWays.setEnabled(((JList<?>)e.getSource()).getSelectedIndex() != -1);
            forClosedWays.setEnabled(forWays.isEnabled());
            notForNodes.setEnabled(forWays.isEnabled());
            onlyForUntaggedObjects.setEnabled(forWays.isEnabled());
            setIconMenu.setEnabled(forWays.isEnabled());
            
            NodeTemplate selected = getSelectedTemplate();
            deleteItem.setText(tr("Delete selected node template"));
            delete.setTooltip(deleteItem.getText());
            
            if(selected == NodeTemplateList.SEPARATOR) {
              addSeparator.setEnabled(false);
              forWays.setEnabled(false);
              forClosedWays.setEnabled(false);
              notForNodes.setEnabled(false);
              onlyForUntaggedObjects.setEnabled(false);
              setIconMenu.setEnabled(false);
              deleteItem.setText(tr("Delete separator"));
              delete.setTooltip(deleteItem.getText());
            }
            else if(selected != null) {
              addSeparator.setEnabled(nodeList.getSelectedIndex() != 0);
              forWays.setSelected(selected.isForWays());
              forClosedWays.setSelected(selected.isForClosedWays());
              notForNodes.setSelected(selected.isNotForNodes());
              onlyForUntaggedObjects.setSelected(selected.isOnlyForUntaggedObjects());
            }
            else {
              addSeparator.setEnabled(false);
            }
            
            popupMenu.show(e.getComponent(), e.getPoint().x, e.getPoint().y);
          }
        }
      }
    };
    
    
    ListSelectionListener selectionListener = e -> {
      if(!e.getValueIsAdjusting() && ((JList<?>)e.getSource()).getSelectedIndex() != -1) {
        clearNodeTemplateSelection(e.getSource());
      }
      
      updateAutoTagEnabledState();
    };
    
    for(JList<NodeTemplate> l : nodeLists) {
      l.addMouseListener(mouseListener);
      l.addListSelectionListener(selectionListener);
    }

    autoTagActionListener = e -> {
      if(e != null && ((e.getModifiers() & ActionEvent.ALT_MASK) == ActionEvent.ALT_MASK)) {
        autoTagSelection.setSelected(true);
      }
      
      if(autoTagSelection.isSelected()) {
        if(clearSelectionAfterApplyingTag) {
          OsmDataManager.getInstance().getActiveDataSet().clearSelection();
        }
        
        if(e != null) {
          noAutoOffTimer = ((e.getModifiers() & ActionEvent.ALT_MASK) == ActionEvent.ALT_MASK) && ((e.getModifiers() & ActionEvent.SHIFT_MASK) == ActionEvent.SHIFT_MASK);
        }
        
        MainApplication.getMap().keyDetector.addKeyListener(keyListener);
        keyListenerAdded = true;
        checkStartTimer();
      }
      else {
        timer = 0;
        noAutoOffTimer = false;
      }
      
      if(!isNodeTemplateSelected() || !autoTagSelection.isSelected()) {
        timer = 0;
      }
    };
    
    autoTagSelection = new JCheckBox(tr("Auto tag selection"));
    autoTagSelection.setToolTipText(tr("If activated tags of selected template are automatically added to selected objects in the map view."));
    autoTagSelection.setEnabled(false);
    autoTagSelection.addActionListener(autoTagActionListener);
    
    autoOffLabel = new JLabel(tr("for {0} seconds"));
    autoOffLabel.setVisible(false);
    
    JPanel autoTagPanel = new JPanel(new BorderLayout(10, 0));
    autoTagPanel.add(autoTagSelection, BorderLayout.WEST);
    autoTagPanel.add(autoOffLabel, BorderLayout.EAST);
    autoTagSelection.setVisible(Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION, true));
    
    prefListener = e -> {
      resetModels();
      refillLists(true);
    };
    
    prefListener2 = e -> {
      if(Boolean.parseBoolean((String)e.getNewValue().getValue())) {
        autoTagPanel.setVisible(true);
      }
      else {
        autoTagPanel.setVisible(false);
      }
      
      updateWayTagging();
      titleBar.invalidate();
    };
    
    prefListener3 = e -> {
      updateWayTagging();
    };
    
    prefListener4 = e -> {
      deactivateAutoTaggingIfNotCompatible = Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_DEACTIVATE, false);
      
      if(deactivateAutoTaggingIfNotCompatible && autoTagSelection.isSelected() && OsmDataManager.getInstance().getActiveDataSet() != null) {
        Collection<OsmPrimitive> selection = OsmDataManager.getInstance().getActiveDataSet().getSelectedNodesAndWays();
        NodeTemplate t = getSelectedTemplate();
        
        boolean found = false;
        
        for(OsmPrimitive p : selection) {
          if(t.isCompatible(p)) {
            found = true;
            break;
          }
        }
        
        if(!found) {
          autoTagSelection.setSelected(false);
          timer = 0;
        }
      }
    };
    
    prefListener5 = e -> {
      clearSelectionAfterApplyingTag = Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_CLEAR_SELECTION_AFTER_TAGGING, true);
    };
    
    p = new JPanel(new GridBagLayout());
    
    JScrollPane west = new JScrollPane(nodeList);
    west.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    
    int column = 1;
    
    p.add(west, GBC.std(column++, 1).fill());
    
    for(int i = 1; i < nodeLists.size(); i++) {
      JSeparator middle = new JSeparator(JSeparator.VERTICAL);
      middle.setVisible(false);
      
      JScrollPane east = new JScrollPane(nodeLists.get(i));
      east.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      east.setVisible(false);
      
      p.add(middle, GBC.std(column++, 1).fill(GBC.VERTICAL));
      p.add(east, GBC.std(column++, 1).fill());
    }
    
    west.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        refillLists();
      }
    });
        
    createLayout(p, false, Arrays.asList(this.btnAdd, this.btnCopy, this.btnPaste, this.btnEdit, this.btnDelete));
    
    for(int i = 0; i < titleBar.getComponentCount(); i++) {
      if(titleBar.getComponent(i) instanceof JPanel) {
        JButton settings = new JButton(ImageProvider.get("preference", ImageProvider.ImageSizes.SMALLICON));
        settings.setBorder(BorderFactory.createEmptyBorder());
        settings.addActionListener(e -> {
          JPopupMenu m = prefMenu;
          
          if(m != null) {
            m.setVisible(false);
          }
          else {
            prefMenu = new JPopupMenu();
            prefMenu.addPopupMenuListener(new PopupMenuListener() {
              @Override
              public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
              @Override
              public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                new Thread() {
                  public void run() {
                    try {
                      Thread.sleep(500);
                    } catch (InterruptedException e) {
                      // ignore
                    }
                    
                    prefMenu = null;
                  }
                }.start();
              }
              @Override
              public void popupMenuCanceled(PopupMenuEvent e) {}
            });
            
            JMenu numberOfLists = new JMenu(tr("Maximum number of columns"));
            int currentNumber = Config.getPref().getInt(PREF_KEY_MAX_NUMBER_OF_LIST_COLUMNS, MAX_LIST_COLUMNS_NUMBER_DEFAULT);
            
            for(int k = 1; k <= MAXIMUM_LIST_COLUMN_NUMBER; k++) {
              final int number = k;
              
              JCheckBoxMenuItem item = new JCheckBoxMenuItem(String.valueOf(k));
              item.setSelected(k == currentNumber);
              item.addActionListener(a -> {
                Config.getPref().putInt(PREF_KEY_MAX_NUMBER_OF_LIST_COLUMNS, number);
              });
              
              numberOfLists.add(item);
            }
            
            prefMenu.add(numberOfLists);
            
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(tr("Add tags to selected objects when selecting template"));
            item.setSelected(Config.getPref().getBoolean(PREF_KEY_TAG_SELECTION, true));
            item.addActionListener(a -> Config.getPref().putBoolean(PREF_KEY_TAG_SELECTION, !Config.getPref().getBoolean(PREF_KEY_TAG_SELECTION, true)));
            
            prefMenu.add(item);
            prefMenu.addSeparator();
            
            boolean autoTag = Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION, true);
            
            item = new JCheckBoxMenuItem(tr("Auto tag objects that are selected from template"));
            item.setSelected(autoTag);
            item.addActionListener(a -> Config.getPref().putBoolean(PREF_KEY_AUTO_TAG_SELECTION, !Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION, true)));
            
            prefMenu.add(item);
            
            item = new JCheckBoxMenuItem(tr("While auto tagging clear selection after applying tags to objects"));
            item.setSelected(clearSelectionAfterApplyingTag);
            item.addActionListener(a -> Config.getPref().putBoolean(PREF_KEY_AUTO_TAG_CLEAR_SELECTION_AFTER_TAGGING, !clearSelectionAfterApplyingTag));
            
            prefMenu.add(item);
            
            item = new JCheckBoxMenuItem(tr("Activate auto tagging when selecting template"));
            item.setSelected(Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_ACTIVATE, true));
            item.addActionListener(a -> Config.getPref().putBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_ACTIVATE, !Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_ACTIVATE, true)));
            item.setEnabled(autoTag);
            
            prefMenu.add(item);

            item = new JCheckBoxMenuItem(tr("Deactivate auto tagging when selecting object not compatible to selected node template"));
            item.setSelected(deactivateAutoTaggingIfNotCompatible);
            item.addActionListener(a -> Config.getPref().putBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_DEACTIVATE, !deactivateAutoTaggingIfNotCompatible));
            item.setEnabled(autoTag);
            
            prefMenu.add(item);

            JMenu autoOff = new JMenu(tr("Auto off timer for auto tagging"));
            autoOff.setEnabled(autoTag);
            
            int timer = Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10);
            
            ActionListener timerAction = ta -> {
              String value = ((JCheckBoxMenuItem)ta.getSource()).getText();
              
              int toSet = 0;
              
              if(value.matches("\\d+")) {
                toSet = Integer.parseInt(value);
              } 
              
              Config.getPref().putInt(PREF_KEY_SELECTION_AUTO_OFF, toSet);
              
              if(autoTagSelection.isSelected()) {
                checkStartTimer();
              }
            };
            
            JCheckBoxMenuItem timerOff = new JCheckBoxMenuItem(tr("disabled"), timer == 0);
            timerOff.addActionListener(timerAction);
            JCheckBoxMenuItem timer3 = new JCheckBoxMenuItem("3", timer == 3);
            timer3.addActionListener(timerAction);
            JCheckBoxMenuItem timer5 = new JCheckBoxMenuItem("5", timer == 5);
            timer5.addActionListener(timerAction);
            JCheckBoxMenuItem timer10 = new JCheckBoxMenuItem("10", timer == 10);
            timer10.addActionListener(timerAction);
            JCheckBoxMenuItem timer20 = new JCheckBoxMenuItem("20", timer == 20);
            timer20.addActionListener(timerAction);
            JCheckBoxMenuItem timer30 = new JCheckBoxMenuItem("30", timer == 30);
            timer30.addActionListener(timerAction);
            
            autoOff.add(timerOff);
            autoOff.add(timer3);
            autoOff.add(timer5);
            autoOff.add(timer10);
            autoOff.add(timer20);
            autoOff.add(timer30);
            
            prefMenu.add(autoOff);
            
            prefMenu.show((JButton)e.getSource(), ((JButton)e.getSource()).getWidth(), ((JButton)e.getSource()).getHeight());
          }
        });
        
        titleBar.add(settings, i+2);
        
        break;
      }      
    }
    
    titleBar.add(autoTagPanel, GBC.std(0,1).insets(0, 0, 5, 0).span(GBC.REMAINDER).fill(GBC.HORIZONTAL));
    
    p.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        resizeNodeLists(false);
      }
    });
    
    final Shortcut disableAutoTagging = Shortcut.registerShortcut(tr("Node Template List: Deactivate auto tagging"), tr("Node Template List: Deactivate auto tagging"), KeyEvent.VK_V, Shortcut.DIRECT);
    
    keyListener = new KeyPressReleaseListener() {
      @Override
      public void doKeyPressed(KeyEvent e) {
        ctrl = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) == MouseEvent.CTRL_DOWN_MASK;
        shift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) == MouseEvent.SHIFT_DOWN_MASK;
        
        if(disableAutoTagging.isEvent(e)) {
          setAutoTagSelectionSelected(false);
          MainApplication.getMap().keyDetector.removeKeyListener(this);
          ctrl = false;
          shift = false;
        }
      }

      @Override
      public void doKeyReleased(KeyEvent e) {
        ctrl = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) == MouseEvent.CTRL_DOWN_MASK;
        shift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) == MouseEvent.SHIFT_DOWN_MASK;
      }
    };
    
    dataSetListener = new DataSetListener() {
      @Override
      public void wayNodesChanged(WayNodesChangedEvent event) {
        timer = Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10) * 1000 + 500;
      }
      
      @Override
      public void tagsChanged(TagsChangedEvent event) {}
      @Override
      public void relationMembersChanged(RelationMembersChangedEvent event) {}
      @Override
      public void primitivesRemoved(PrimitivesRemovedEvent event) {}
      @Override
      public void primitivesAdded(PrimitivesAddedEvent event) {}
      @Override
      public void otherDatasetChange(AbstractDatasetChangedEvent event) {}
      @Override
      public void nodeMoved(NodeMovedEvent event) {}
      @Override
      public void dataChanged(DataChangedEvent event) {}
    };
    
    SelectionEventManager.getInstance().addSelectionListenerForEdt(this);
    MainApplication.getLayerManager().addActiveLayerChangeListener(this);
  }
  
  private boolean updateAutoTagEnabledState() {
    NodeTemplate t = getSelectedTemplate();
    
    if(t != null && t.isEnabled(true) && sortDialog == null) {
      autoTagSelection.setEnabled(true);
      
      return true;
    }
    else {
      setAutoTagSelectionSelected(false);
      autoTagSelection.setEnabled(false);
    }
    
    return false;
  }
  
  private void repaintLists() {
    p.repaint();
  }
  
  private void updateWayTagging() {
    wayTaggingPossible = Config.getPref().getBoolean(PREF_KEY_TAG_SELECTION, true) ||Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION, true);
    
    repaintLists();
    
    updateBtnEnabledState();
    updateAutoTagEnabledState();
  }
  
  private void setAutoTagSelectionSelected(boolean value) {
    autoTagSelection.setSelected(value);
    
    autoTagActionListener.actionPerformed(null);
  }
    
  @Override
  public void showDialog() {
    if(titleBar != null) {
      super.showDialog();
      
      SwingUtilities.invokeLater(() -> refillLists(true));
    }
  }
  
  private void resizeNodeLists(boolean force) {
    Component west = p.getComponent(0);
    
    int visibleLists = 1;
    int width = p.getWidth();
    
    for(int i = 2; i < p.getComponentCount(); i++) {
      if(p.getComponent(i-1) instanceof JScrollPane) {
        visibleLists++;
      }
      if(!p.getComponent(i).isVisible()) {
        break;
      }
      else if(p.getComponent(i) instanceof JSeparator) {
        width -= p.getComponent(i).getPreferredSize().width;
      }
    }
    
    width /= visibleLists;
    
    if(force || west.getPreferredSize().width != width || panelBounds == null || !panelBounds.equals(p.getBounds())) {
      Dimension d = new Dimension(width, 10);
      
      for(int i = 0; i < p.getComponentCount(); i++) {
        if(p.getComponent(i) instanceof JScrollPane) {
          p.getComponent(i).setPreferredSize(d);
        }
        else if(!p.getComponent(i).isVisible()) {
          break;
        }
      }
      p.revalidate();
    }
  }
  
  @Override
  protected void stateChanged() {
    updateTitleBarVisibility();
  }
  
  private void updateTitleBarVisibility() {
    titleBar.getComponent(0).setVisible(isDocked);
    titleBar.getComponent(titleBar.getComponentCount()-2).setVisible(isDocked);
    titleBar.getComponent(titleBar.getComponentCount()-3).setVisible(isDocked);
    titleBar.setVisible(true);
  }
  
  private synchronized void checkStartTimer() {
    timer = Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10) * 1000 + 500;
    
    if(!noAutoOffTimer && timer > 500 && (autoOffTimer == null || !autoOffTimer.isAlive())) {
      autoOffTimer = new Thread() {
        public void run() {
          try {
            DatasetEventManager.getInstance().addDatasetListener(dataSetListener, FireMode.IMMEDIATELY);
            
            SwingUtilities.invokeLater(() -> {
              autoOffLabel.setText(tr("for {0} seconds", String.format("%02d", Math.round(timer/1000))));
              autoOffLabel.setVisible(true);
            });
            
            while((timer-= 200) > 500) {
              if(noAutoOffTimer) {
                break;
              }
              try {
                Thread.sleep(200);
              } catch (InterruptedException e) {
                // ignore
              }
              
              SwingUtilities.invokeLater(() -> autoOffLabel.setText(tr("for {0} seconds", String.format("%02d", Math.round(timer/1000)))));
            }
            
            SwingUtilities.invokeLater(() -> {
              autoOffLabel.setVisible(false);
              removeKeyListenerIfSet();
              
              if(!noAutoOffTimer && Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10) != 0) {
                setAutoTagSelectionSelected(false);
              }
            });
          }
          finally {
            DatasetEventManager.getInstance().removeDatasetListener(dataSetListener);
          }
        }
      };
      autoOffTimer.start();
    }
  }
  
  private boolean isSelectedTemplateNotForNodes() {
    NodeTemplate t = getSelectedTemplate();
    
    if(t != null) {
      return t.isNotForNodes();
    }
    
    return false;
  }
  
  private boolean isNodeTemplateUsable() {
    for(JList<NodeTemplate> list : nodeLists) {
      if(list != null && list.getSelectedIndex() >= 0 && list.isEnabled()) {
        return true;
      }
    }
    
    return false;
  }
  
  private void repaintSelectedRow() {
    for(JList<NodeTemplate> list : nodeLists) {
      if(list.getSelectedIndex() != -1) {
        repaintSelectedRow(list);
        break;
      }
    }
  }
  
  private void clearNodeTemplateSelection(Object test) {
    for(JList<NodeTemplate> list : nodeLists) {
      if(list != test) {
        list.clearSelection();
      }
    }
  }
  
  private boolean isNodeTemplateSelected() {
    for(JList<NodeTemplate> list : nodeLists) {
      if(list.getSelectedIndex() != -1) {
        return true;
      }
    }
    
    return false;
  }
  
  private NodeTemplate getSelectedTemplate() {
    for(JList<NodeTemplate> list : nodeLists) {
      if(list.getSelectedIndex() != -1) {
        return list.getSelectedValue();
      }
    }
    
    return null;
  }
  
  private void setSelectedNodeTemplate(NodeTemplate selected) {
    for(int i = 0; i < models.size(); i++) {
      if(models.get(i).contains(selected)) {
        nodeLists.get(i).setSelectedValue(selected, true);
        break;
      }
    }
  }
  
  private void resetModels() {
    for(int k = 1; k < models.size(); k++) {
      for(int i = 0; i < models.get(k).size(); i++) {
        model.addElement(models.get(k).get(i));
      }
      
      models.get(k).clear();
    }
  }
  
  private void addNodeTemplate(NodeTemplate t) {
    DefaultListModel<NodeTemplate> m = null;
    
    for(int i = models.size()-1; i >= 1; i--) {
      if(!models.get(i).isEmpty()) {
        m = models.get(i);
        break;
      }
    }
    
    if(m != null) {
      m.addElement(t);
    }
    else {
      model.addElement(t);
    }
    
    NodeTemplateList.get().add(t);
  }
  
  private synchronized void refillLists() {
    refillLists(false);
  }
  
  private synchronized void refillLists(boolean force) {
    if(model.isEmpty()) {
      NodeTemplateList.get().fillModel(model);
    }
    
    if(force || panelBounds == null || !panelBounds.equals(p.getBounds())) {
      int numberOfLists = Config.getPref().getInt(PREF_KEY_MAX_NUMBER_OF_LIST_COLUMNS, MAX_LIST_COLUMNS_NUMBER_DEFAULT);
      NodeTemplate selected = getSelectedTemplate();
      
      if(numberOfLists > 1) {
        int split = nodeList.getVisibleRowCount();
        
        Rectangle a = nodeList.getVisibleRect();
        
        int rowHeight = 0;
        
        if(model.size() > 0) {
          rowHeight = nodeList.getCellBounds(0, 0).height;
          split = a.height/rowHeight;
        }
        
        if(split != 0) {
          LinkedList<NodeTemplate> entries = new LinkedList<>();
              
          for(int k = 0; k < models.size(); k++) {
            for(int i = 0; i < models.get(k).size(); i++) {
              if(!entries.isEmpty() || models.get(k).get(i) != NodeTemplateList.SEPARATOR) {
                entries.add(models.get(k).get(i));
              }
            }
            
            models.get(k).clear();
          }
                    
          for(int k = entries.size()-1; k >= 0; k--) {
            if(entries.get(k) == NodeTemplateList.SEPARATOR) {
              entries.remove(k);
            }
            else {
              break;
            }
          }
          
          int n = split;
          
          if(entries.size() >= split*numberOfLists) {
            n = entries.size() / numberOfLists;
            
            if(entries.size() % numberOfLists != 0) {
              n++;
            }
          }
          
          int count = 0;
          int index = 0;
          
          DefaultListModel<NodeTemplate> m = models.get(index);
          int heightUsed = 0;
          int listsEntryCount = 0;
          
          for(NodeTemplate t : entries) {
            if(rowHeight > 0) {
              if(t == NodeTemplateList.SEPARATOR) {
                heightUsed += HEIGHT_SEPARATOR;
              }
              else {
                heightUsed += rowHeight;
              }
            } 
            
            if(++count > n && heightUsed > a.height) {
              int rest = entries.size() - listsEntryCount;
              
              n = rest / (numberOfLists-index-1);
              
              if(rest % (numberOfLists-index-1) != 0) {
                n++;
              }

              m = models.get(++index);
              count = 1;
              
              if(t == NodeTemplateList.SEPARATOR) {
                heightUsed = HEIGHT_SEPARATOR;
              }
              else {
                heightUsed = rowHeight;
              }
            }

            listsEntryCount++;
            m.addElement(t);
          }
          
          count = 0;
          
          for(int i = 0; i < p.getComponentCount(); i++) {
            if(count >= Math.min(index+1, numberOfLists)) {
              p.getComponent(i).setVisible(false);
            }
            else {
              p.getComponent(i).setVisible(true);
            }
              
            if(p.getComponent(i) instanceof JScrollPane) {
              nodeLists.get(count).setVisible(p.getComponent(i).isVisible());
              count++;
            }
          }
        }
      }
      else {
        resetModels();
        
        p.getComponent(0).setPreferredSize(new Dimension(p.getWidth(),10));
        
        for(int i = 1; i < p.getComponentCount(); i++) {
          p.getComponent(i).setVisible(false);
        }
      }
      
      resizeNodeLists(false);
      panelBounds = p.getBounds();
      setAutoTagSelectionSelected(false);
      
      if(selected != null && selected != NodeTemplateList.SEPARATOR) {
        setSelectedNodeTemplate(selected);
      }
      
      if(sortDialog != null) {
        sortDialog.updateBtnEnabledState(this);
      }
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
      NodeTemplate n = getSelectedTemplate();
      
      if(n != null) {
        n.setIconName(iconName);
        
        NodeTemplateList.get().updateQuickAccess(n);
        
        repaintSelectedRow();
      }
    }
  }
  
  private class PresetAction extends AbstractAction {
    private Action parent;
    private String name;
    
    private PresetAction(JMenuItem preset) {      
      parent = preset.getAction();
      name = preset.getText();
      putValue(NAME, preset.getText());
      putValue(SHORT_DESCRIPTION, parent.getValue(SHORT_DESCRIPTION));
      putValue(SMALL_ICON, parent.getValue(SMALL_ICON));
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      if(parent != null) {
        autoTagDisabled = true;
        Collection<OsmPrimitive> curSel = OsmDataManager.getInstance().getActiveDataSet().getAllSelected();
        
        boolean isWay = ((TaggingPreset) parent).types.contains(TaggingPresetType.WAY);
        boolean isNode = (((TaggingPreset) parent).types.contains(TaggingPresetType.NODE));
        
        EastNorth center = MainApplication.getMap().mapView.getCenter();
        
        Node n1 = new Node(center);
        OsmDataManager.getInstance().getActiveDataSet().addPrimitive(n1);
        
        Node n2 = null;
        OsmPrimitive toUse = n1;
        
        Way w = null;
        
        if(!isNode) {          
          n2 = new Node(center.add(5, 5));
          
          OsmDataManager.getInstance().getActiveDataSet().addPrimitive(n2);
         
          w = new Way();
          toUse = w;
          w.addNode(n1);
          w.addNode(n2);
          OsmDataManager.getInstance().getActiveDataSet().addPrimitive(w);
        }
        
        OsmDataManager.getInstance().getActiveDataSet().setSelected(toUse);
        parent.actionPerformed(e);

        if(!toUse.getKeys().isEmpty()) {
          NodeTemplate t = new NodeTemplate(((TaggingPreset)parent).iconName, toUse);
          
          if(name != null) {
            t.setName(name);
          }
          
          if(((TaggingPreset) parent).types != null) {
            t.setNotForNodes(!isNode);
            t.setForWays(isWay);
          }
          
          addNodeTemplate(t);
        }
        
        if(w != null) {
          OsmDataManager.getInstance().getActiveDataSet().removePrimitive(w);
        }
        
        if(n1 != null) {
          OsmDataManager.getInstance().getActiveDataSet().removePrimitive(n1);
        }
        
        if(n2 != null) {
          OsmDataManager.getInstance().getActiveDataSet().removePrimitive(n2);
        }
        
        OsmDataManager.getInstance().getActiveDataSet().clearSelection();
                
        refillLists(true);
        OsmDataManager.getInstance().getActiveDataSet().setSelected(curSel);
        autoTagDisabled = false;
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
        
        if(a instanceof TaggingPreset && ((TaggingPreset) a).types != null && (((TaggingPreset) a).types.contains(TaggingPresetType.NODE) || ((TaggingPreset) a).types.contains(TaggingPresetType.WAY))) {
          if(wayTaggingPossible || (((TaggingPreset) a).types.contains(TaggingPresetType.NODE))) {
            parent.add(new PresetAction((JMenuItem)subMenu));
          }
          
          if(subMenu instanceof JMenu || ((JMenuItem)subMenu).getIcon() != null) {
            parent2.add(new IconAction((JMenuItem)subMenu));
          }
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
    popupMenu.add(sortItem);
    popupMenu.add(sortManually);
    popupMenu.add(addSeparator);
    popupMenu.addSeparator();
    popupMenu.add(copy);
    popupMenu.add(paste);
    popupMenu.addSeparator();
    popupMenu.add(forWays);
    popupMenu.add(forClosedWays);
    popupMenu.add(notForNodes);
    popupMenu.add(onlyForUntaggedObjects);
    popupMenu.addSeparator();
    popupMenu.add(setIconMenu);
    popupMenu.add(edit);
    deleteItem = popupMenu.add(delete);
  }
  
  @Override
  public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) { 
    if(dataSetEnabledStateListener != null && e.getPreviousDataSet() != null) {
      e.getPreviousDataSet().removeDataSetListener(dataSetEnabledStateListener);
    }
    
    if(dataSetEnabledStateListener == null) {
      dataSetEnabledStateListener = new DataSetListener() {
        @Override
        public void wayNodesChanged(WayNodesChangedEvent event) {
          NodeTemplateList.get().updateQuickMenuItemsEnabledState();
        }
        
        @Override
        public void tagsChanged(TagsChangedEvent event) {}
        @Override
        public void relationMembersChanged(RelationMembersChangedEvent event) {}
        @Override
        public void primitivesRemoved(PrimitivesRemovedEvent event) {}
        @Override
        public void primitivesAdded(PrimitivesAddedEvent event) {}
        @Override
        public void otherDatasetChange(AbstractDatasetChangedEvent event) {}
        @Override
        public void nodeMoved(NodeMovedEvent event) {}
        @Override
        public void dataChanged(DataChangedEvent event) {}
      };
    }
    
    Layer layer = e.getSource().getActiveLayer();
    OsmDataLayer dataLayer = e.getSource().getActiveDataLayer();
    
    if(dataLayer != null && dataLayer.getDataSet() != null) {
      dataLayer.getDataSet().addDataSetListener(dataSetEnabledStateListener);
    }
    
    nodeList.setEnabled(layer != null && !layer.isBackgroundLayer() && dataLayer != null && !dataLayer.isLocked());
    updateBtnEnabledState();
  }
  
  private void handleSelection(Collection<OsmPrimitive> selection, boolean selectionTag, boolean ctrl, boolean shift, boolean clearSelection) {
    handleSelection(getSelectedTemplate(), selection, selectionTag, ctrl, shift, clearSelection);
  }
  
  void handleSelection(NodeTemplate t, Collection<OsmPrimitive> selection, boolean selectionTag, boolean ctrl, boolean shift, boolean clearSelection) {
    if(selectionTag) {
      setAutoTagSelectionSelected(false);
      timer = 0;
    }
    
    if(((autoTagSelection.isVisible() && autoTagSelection.isSelected()) || selectionTag) && !autoTagDisabled) {
      if(!selection.isEmpty()) {
        selection.forEach(p -> {
          final AtomicBoolean found = new AtomicBoolean();
          
          if(t != null) {
            final ArrayList<Command> cmds = t.createChangeCommand(p, ctrl, shift, found, deactivateAutoTaggingIfNotCompatible);
            
            if(found.get()) {
              timer = Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10) * 1000 + 500;
            }
            
            if(!cmds.isEmpty()) {
              if(clearSelection && found.get() && !Objects.equals(MainApplication.getMap().mapModeDraw.getValue("active"), Boolean.TRUE)) {
                cmds.add(new ClearSelectionCommand());
              }
              
              UndoRedoHandler.getInstance().add(new SequenceCommand("add template to selection", cmds));
            }
          }
          
          if(!found.get() && deactivateAutoTaggingIfNotCompatible) {
            autoTagSelection.setSelected(false);
            timer = 0;
          }
        });
      }
    }
  }
  
  @Override
  public synchronized void selectionChanged(SelectionChangeEvent event) {
    add.updateEnabledState();
    add.setEnabled(sortDialog == null && getSelectedTemplate() != NodeTemplateList.SEPARATOR);
    
    handleSelection(event.getSelection(), false, ctrl, shift, clearSelectionAfterApplyingTag);
    
    repaintLists();
    updateAutoTagEnabledState();
    NodeTemplateList.get().updateQuickMenuItemsEnabledState();
  }

  private void updateBtnEnabledState() {
    sortItem.setEnabled(sortDialog == null && model.size() > 1 && nodeList.isEnabled());
    add.updateEnabledState();
    edit.updateEnabledState();
    copy.updateEnabledState();
    paste.updateEnabledState();
    delete.updateEnabledState();
    
    boolean separator = getSelectedTemplate() == NodeTemplateList.SEPARATOR;
    
    add.setEnabled(add.isEnabled() && !separator);
    edit.setEnabled(edit.isEnabled() && !separator);
    copy.setEnabled(copy.isEnabled() && !separator);
    paste.setEnabled(paste.isEnabled() && !separator);
    
    if(separator) {
      deleteItem.setText(tr("Delete separator"));
    }
    else {
      deleteItem.setText(tr("Delete selected node template"));
    }
    
    delete.setTooltip(deleteItem.getText());
    
    if(sortDialog != null) {
      sortDialog.updateBtnEnabledState(this);
    }
    
    NodeTemplateList.get().updateQuickMenuItemsEnabledState();
  }
  
  private void repaintSelectedRow(JList<NodeTemplate> nodeList) {
    int index = nodeList.getSelectedIndex();
    if (index >= 0) {
      nodeList.repaint(nodeList.getCellBounds(index, index));
    }
  }
    
  private Map<String,String> createMapFromText(String text) {
    Map<String,String> map = new LinkedHashMap<>();
    
    if(text.indexOf("=") > 0 && text.indexOf("=") < text.length()-1) {
      String[] lines = text.split(System.lineSeparator(), -1);
      
      for(String line : lines) {
        String[] tag = line.split("=", -1);
        
        if(tag.length == 2) {
          map.put(tag[0], tag[1]);
        }
      }
    }
    
    return map;
  }
  
  private NodeTemplate editNodeTemplate(NodeTemplate t) {
    if(t == null) {
      t = new NodeTemplate(tr("New Node Template"));
    }
    
    JTextField name = new JTextField(t.toString());
    name.setPreferredSize(new Dimension(350, name.getPreferredSize().height));
    name.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        SwingUtilities.invokeLater(() -> {
          name.requestFocusInWindow();
          name.selectAll();          
        });
        
        name.removeComponentListener(this);
      }
    });
        
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(name, BorderLayout.CENTER);
    
    if(t.getIcon() != null) {
      JLabel icon = new JLabel(t.getIcon());
      icon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
      panel.add(icon, BorderLayout.WEST);
    }
    
    IconCheckBox forWays = new IconCheckBox(tr("Allow usage for unclosed ways on selection"), ImageProvider.get(OsmPrimitiveType.WAY), t.isForWays());
    IconCheckBox forClosedWays = new IconCheckBox(tr("Allow usage for closed ways on selection"), ImageProvider.get(OsmPrimitiveType.CLOSEDWAY), t.isForWays());
    IconCheckBox notForNodes = new IconCheckBox(tr("Not for nodes"), ImageProvider.get("not_for_nodes", ImageProvider.ImageSizes.MENU), t.isNotForNodes());
    IconCheckBox onlyForUntagged = new IconCheckBox(tr("Only for untagged objects"), ImageProvider.get("presets/misc/no_icon", ImageProvider.ImageSizes.MENU), t.isOnlyForUntaggedObjects());
    
    JTextArea tags = new JTextArea();
    JScrollPane tagsScroll = new JScrollPane(tags);
    tagsScroll.setPreferredSize(new Dimension(350,150));
    
    JTextArea ctrl = new JTextArea();
    JScrollPane ctrlScroll = new JScrollPane(ctrl);
    ctrlScroll.setPreferredSize(new Dimension(350,50));
    
    JTextArea shift = new JTextArea();
    JScrollPane shiftScroll = new JScrollPane(shift);
    shiftScroll.setPreferredSize(new Dimension(350,50));
    
    JPanel content = new JPanel(new GridBagLayout());
    GBC gbc = GBC.std(0,0);
    
    content.add(new JLabel(tr("Name:")), gbc);
    content.add(panel, gbc.grid(0, gbc.gridy+1).fill(GBC.HORIZONTAL));
    content.add(new JLabel(tr("Tags (one per line):")), gbc.grid(0, gbc.gridy+1).insets(0, 10, 0, 0).fill(GBC.NONE));
    content.add(tagsScroll, gbc.grid(0, gbc.gridy+1).insets(0).fill(GBC.HORIZONTAL));
    content.add(new JLabel(tr("Optional tags with Ctrl pressed:")), gbc.grid(0, gbc.gridy+1).insets(0, 10, 0, 0).fill(GBC.NONE));
    content.add(ctrlScroll, gbc.grid(0, gbc.gridy+1).insets(0).fill(GBC.HORIZONTAL));
    content.add(new JLabel(tr("Optional tags with Shift pressed:")), gbc.grid(0, gbc.gridy+1).insets(0, 10, 0, 0).fill(GBC.NONE));
    content.add(shiftScroll, gbc.grid(0, gbc.gridy+1).insets(0).fill(GBC.HORIZONTAL));
    content.add(forWays, gbc.grid(0, gbc.gridy+1).insets(0, 10, 0, 0).fill(GBC.HORIZONTAL));
    content.add(forClosedWays, gbc.grid(0, gbc.gridy+1).insets(0, 2, 0, 0).fill(GBC.HORIZONTAL));
    content.add(notForNodes, gbc.grid(0, gbc.gridy+1).fill(GBC.HORIZONTAL));
    content.add(onlyForUntagged, gbc.grid(0, gbc.gridy+1).fill(GBC.HORIZONTAL));
    
    StringBuilder b = new StringBuilder();
    
    t.getMap().forEach((key,value) -> b.append(key).append("=").append(value).append(System.lineSeparator()));
    
    tags.setText(b.toString().strip());
    tags.setCaretPosition(0);
    
    b.setLength(0);
    
    t.getCtrl().forEach((key,value) -> b.append(key).append("=").append(value).append(System.lineSeparator()));
    
    ctrl.setText(b.toString().strip());
    ctrl.setCaretPosition(0);
    
    b.setLength(0);
    
    t.getShift().forEach((key,value) -> b.append(key).append("=").append(value).append(System.lineSeparator()));
    
    shift.setText(b.toString().strip());
    shift.setCaretPosition(0);
    
    if(JOptionPane.OK_OPTION == JOptionPane.showConfirmDialog(MainApplication.getMainFrame(), content, tr("Edit node template"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) {
      if(!name.getText().isBlank()) {
        t.setName(name.getText().strip());
      }
      
      t.setMap(createMapFromText(tags.getText()));     
      t.setCtrl(createMapFromText(ctrl.getText()));
      t.setShift(createMapFromText(shift.getText()));
      
      t.setForWays(forWays.isSelected());
      t.setForClosedWays(forClosedWays.isSelected());
      t.setNotForNodes(notForNodes.isSelected());
      t.setOnlyForUntaggedObjects(onlyForUntagged.isSelected());
      
      return t;
    }
    
    return null;
  }

  @Override
  public void showNotify() {
    Config.getPref().addKeyPreferenceChangeListener(PREF_KEY_MAX_NUMBER_OF_LIST_COLUMNS, prefListener);
    Config.getPref().addKeyPreferenceChangeListener(PREF_KEY_AUTO_TAG_SELECTION, prefListener2);
    Config.getPref().addKeyPreferenceChangeListener(PREF_KEY_TAG_SELECTION, prefListener3);
    Config.getPref().addKeyPreferenceChangeListener(PREF_KEY_AUTO_TAG_SELECTION_AUTO_DEACTIVATE, prefListener4);
    Config.getPref().addKeyPreferenceChangeListener(PREF_KEY_AUTO_TAG_CLEAR_SELECTION_AFTER_TAGGING, prefListener5);
    updateBtnEnabledState();
  }

  private synchronized void removeKeyListenerIfSet() {
    if(keyListenerAdded) {
      MainApplication.getMap().keyDetector.removeKeyListener(keyListener);
      keyListenerAdded = false;
    }
  }
  
  @Override
  public synchronized void hideNotify() {
    removeKeyListenerIfSet();
    
    Config.getPref().removeKeyPreferenceChangeListener(PREF_KEY_MAX_NUMBER_OF_LIST_COLUMNS, prefListener);
    Config.getPref().removeKeyPreferenceChangeListener(PREF_KEY_AUTO_TAG_SELECTION, prefListener2);
    Config.getPref().removeKeyPreferenceChangeListener(PREF_KEY_TAG_SELECTION, prefListener3);
    Config.getPref().removeKeyPreferenceChangeListener(PREF_KEY_AUTO_TAG_SELECTION_AUTO_DEACTIVATE, prefListener4);
    Config.getPref().removeKeyPreferenceChangeListener(PREF_KEY_AUTO_TAG_CLEAR_SELECTION_AFTER_TAGGING, prefListener5);
  }

  @Override
  public void destroy() {
    NodeTemplateList.get().save();
    super.destroy();
  }
  
  @Override
  public void collapse() {
    if(isDocked) {
      super.collapse();
    }
    
    revalidate();
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
        boolean added = false;
        
        for (Node node : nodeList) {
          if(!node.getKeys().isEmpty()) {
            addNodeTemplate(new NodeTemplate(node));
            added = true;
          }
        }
        
        final Collection<Way> wayList = OsmDataManager.getInstance().getActiveDataSet().getSelectedWays();
        
        for(Way w : wayList) {
          if(!w.getKeys().isEmpty()) {
            addNodeTemplate(new NodeTemplate(w));
            added = true;
          }
        }
        
        if(!added) {
          NodeTemplate t = editNodeTemplate(null);
          
          if(t != null) {
            addNodeTemplate(t);
          }
        }
      } finally {
        refillLists(true);
        updateBtnEnabledState();
        isPerforming.set(false);
      }
    }

    @Override
    protected final void updateEnabledState() {
      DataSet ds = OsmDataManager.getInstance().getActiveDataSet();
      setEnabled(sortDialog == null && ds != null && !ds.isLocked() && (ds.getSelectedNodesAndWays().isEmpty() || !ds.getSelectedNodes().isEmpty() || (wayTaggingPossible && !ds.getSelectedWays().isEmpty())) && nodeList.isEnabled());
    }
  }
  
  class EditAction extends JosmAction {
    EditAction() {
      super(tr("Edit selected node template"), /* ICON() */ "dialogs/edit", tr("Edit selected node template"), /* Shortcut */ null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      NodeTemplate t = getSelectedTemplate();
      
      if(editNodeTemplate(t) != null) {
        NodeTemplateList.get().updateQuickMenuEntry(t);
        repaintSelectedRow();
      }
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(sortDialog == null && isNodeTemplateUsable());
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
        NodeTemplateList.get().copy(getSelectedTemplate(), e);
      }
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(sortDialog == null && isNodeTemplateUsable() && !isSelectedTemplateNotForNodes());
    }
  }

  class PasteAction extends AbstractPasteAction {
    PasteAction() {
      super(tr("Create new node from selected template and paste it directly into the map view"), /* ICON() */ "paste", tr("Create new node from selected template and paste it directly into the map view"), /* Shortcut */ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      if(isEnabled()) {
        NodeTemplateList.get().paste(getSelectedTemplate(),e);
      }
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(sortDialog == null && isNodeTemplateUsable() && !isSelectedTemplateNotForNodes());
    }
  }
  
  class DeleteAction extends JosmAction {
    DeleteAction() {
      super(tr("Delete selected node template"), /* ICON() */ "dialogs/delete", tr("Delete selected node template"), /* Shortcut */ null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      for(int i = 0; i < models.size(); i++) {
        int index = nodeLists.get(i).getSelectedIndex();
        
        if(index != -1) {
          NodeTemplate t = models.get(i).get(index);
          
          if(t == NodeTemplateList.SEPARATOR || JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(MainApplication.getMainFrame(), tr("Do you really want to delete selected template?"), tr("Delete selected template?"), JOptionPane.YES_NO_OPTION)) {
            NodeTemplateList.get().nodeTemplateDeleted(models.get(i).remove(index));
            
            if(models.get(i).size() >= index) {
              nodeLists.get(i).setSelectedIndex(index-1);
            }
          }
          
          refillLists(true);
          updateBtnEnabledState();
          break;
        }
      }
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(sortDialog == null && isNodeTemplateUsable());
    }
  }
  
  private static final class CheckBoxMenuIcon extends ImageIcon {
    final ImageIcon icon;
    final ImageIcon selected;
    
    public static final void createIcon(JCheckBoxMenuItem item, ImageIcon ic, ImageIcon selectedOverlay, boolean disabledIcon) {
      new CheckBoxMenuIcon(item, ic, selectedOverlay, disabledIcon);
    }
    
    private CheckBoxMenuIcon(JCheckBoxMenuItem item, ImageIcon ic, ImageIcon selectedOverlay, boolean disabledIcon) {
      BufferedImage iconImage = new BufferedImage(ic.getIconWidth(), ic.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
      
      Graphics2D bGr = iconImage.createGraphics();
      ic.paintIcon(null, bGr, 0, 0);
      selectedOverlay.paintIcon(null, bGr, ic.getIconWidth()/2 - selectedOverlay.getIconWidth()/2, ic.getIconHeight()/2 - selectedOverlay.getIconHeight()/2);
      bGr.dispose();
      
      if(disabledIcon) {
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        op.filter(iconImage, iconImage);
        
        BufferedImage iconImage2 = new BufferedImage(ic.getIconWidth(), ic.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        bGr = iconImage2.createGraphics();
        ic.paintIcon(null, bGr, 0, 0);
        bGr.dispose();
        
        ic = new ImageIcon(iconImage2);
      }
      
      icon = ic;
      selected = new ImageIcon(iconImage);
      
      if(disabledIcon) {
        item.setDisabledIcon(this);
      }
      else {
        item.setIcon(this);
      }
    }
    
    @Override
    public int getIconHeight() {
      return icon.getIconHeight();
    }
    
    @Override
    public int getIconWidth() {
      return icon.getIconWidth();
    }
    
    @Override
    public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
      if(((JCheckBoxMenuItem)c).isSelected()) {
        selected.paintIcon(c, g, x, y);
      }
      else {
        icon.paintIcon(c, g, x, y);
      }
    }
  };
  
  private static final class ClearSelectionCommand extends SelectCommand {
    public ClearSelectionCommand() {
      super(OsmDataManager.getInstance().getActiveDataSet(), null);
    }
    
    @Override
    public void undoCommand() {}
    
    @Override
    public boolean executeCommand() {
      SwingUtilities.invokeLater(() -> OsmDataManager.getInstance().getActiveDataSet().clearSelection());
      
      return true;
    }
  }
  
  private static final class IconCheckBox extends JPanel {
    private JCheckBox check;
    
    public IconCheckBox(String text, ImageIcon icon, boolean selected) {
      check = new JCheckBox();
      check.setSelected(selected);
      final JLabel label = new JLabel(text, icon, JLabel.LEADING);
      
      setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
      
      add(check);
      add(Box.createRigidArea(new Dimension(2,0)));
      add(label);
      add(Box.createHorizontalGlue());
      
      MouseAdapter a = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if(SwingUtilities.isLeftMouseButton(e)) {
            check.setSelected(!check.isSelected());
          }
        }
      };
      
      addMouseListener(a);
      label.addMouseListener(a);
    }
    
    public boolean isSelected() {
      return check.isSelected();
    }
  }
  
  private static final class SortDialog extends JDialog {
    private final JButton up;
    private final JButton down;
    private final JButton toTopOfList;
    private final JButton toBottomOfList;
    private final JButton toTop;
    private final JButton toBottom;
    
    public SortDialog(NodeTemplateListDialog d) {
      up = new JButton(ImageProvider.get("up", ImageSizes.LARGEICON));
      up.setToolTipText(tr("Move list entry up by one"));
      up.addActionListener(e -> {
        d.sortUp();
        updateBtnEnabledState(d);
      });
      
      down = new JButton(ImageProvider.get("down", ImageSizes.LARGEICON));
      down.setToolTipText(tr("Move list entry down by one"));
      down.addActionListener(e -> {
        d.sortDown();
        updateBtnEnabledState(d);
      });
      
      toTopOfList = new JButton(ImageProvider.get("to-top-of-list", ImageSizes.LARGEICON));
      toTopOfList.setToolTipText(tr("Move to top of the current list"));
      toTopOfList.addActionListener(e -> {
        d.sortToTopOfList();
        updateBtnEnabledState(d);
      });
      
      toBottomOfList = new JButton(ImageProvider.get("to-bottom-of-list", ImageSizes.LARGEICON));
      toBottomOfList.setToolTipText(tr("Move to end the current list"));
      toBottomOfList.addActionListener(e -> {
        d.sortToBottomOfList();
        updateBtnEnabledState(d);
      });
      
      toTop = new JButton(ImageProvider.get("to-top", ImageSizes.LARGEICON));
      toTop.setToolTipText(tr("Move to begin of first list"));
      toTop.addActionListener(e -> {
        d.sortToTop();
        updateBtnEnabledState(d);
      });
      
      toBottom = new JButton(ImageProvider.get("to-bottom", ImageSizes.LARGEICON));
      toBottom.setToolTipText(tr("Move to end of last list"));
      toBottom.addActionListener(e -> {
        d.sortToBottom();
        updateBtnEnabledState(d);
      });
      
      JButton exit = new JButton(ImageProvider.get("exit", ImageSizes.LARGEICON));
      exit.setToolTipText(tr("Exit sorting"));
      exit.addActionListener(e -> {
        d.stopSortingManually();
        NodeTemplateList.get().updateList(d.models);
        dispose();
      });
      
      JPanel content = new JPanel();
      content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
      
      content.add(toTop);
      content.add(toTopOfList);
      content.add(up);
      content.add(down);
      content.add(toBottomOfList);
      content.add(toBottom);
      content.add(exit);
      
      updateBtnEnabledState(d);
      
      setContentPane(content);
      setUndecorated(true);
      setAlwaysOnTop(true);
      setResizable(false);
      pack();
    }
    
    private void updateBtnEnabledState(NodeTemplateListDialog d) {
      NodeTemplate selected = d.getSelectedTemplate();
      
      up.setEnabled(selected != null && d.nodeList.getSelectedIndex() != 0);
      down.setEnabled(selected != null);
      toTop.setEnabled(up.isEnabled());
      toBottom.setEnabled(selected != null);
      toTopOfList.setEnabled(selected != null);
      toBottomOfList.setEnabled(selected != null);
      
      if(selected != null) {
        for(int i = 0; i < d.nodeLists.size(); i++) {
          JList<NodeTemplate> list = d.nodeLists.get(i);
          
          if(list.getSelectedIndex() != -1) {
            toBottom.setEnabled(i < d.nodeLists.size()-1 && d.nodeLists.get(i+1).isVisible());
            down.setEnabled(list.getSelectedIndex() < list.getModel().getSize()-1 || toBottom.isEnabled());
            toBottomOfList.setEnabled(list.getSelectedIndex() < list.getModel().getSize()-1);
            toTopOfList.setEnabled(list.getSelectedIndex() > 0);
            break;
          }
        }
      }
    }
        
    public void showDialog(Rectangle bounds) {
      Point p = new Point(bounds.x-getWidth(), bounds.y);
      
      if(bounds.x-getWidth() < 0) {
        p.x = bounds.x+bounds.width;
      }
      
      if(bounds.y+getHeight() > getGraphicsConfiguration().getDevice().getDefaultConfiguration().getBounds().height) {
        p.y = getGraphicsConfiguration().getDevice().getDefaultConfiguration().getBounds().height-+getHeight();
      }
      
      setLocation(p);
      setVisible(true);
    }
  }
}