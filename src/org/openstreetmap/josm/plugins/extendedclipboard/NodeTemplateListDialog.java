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
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

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
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.PreferenceChangedListener;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

public class NodeTemplateListDialog extends ToggleDialog implements DataSelectionListener, ActiveLayerChangeListener {
  private static final int MAXIMUM_LIST_COLUMN_NUMBER = 7;
  private static final int MAX_LIST_COLUMNS_NUMBER_DEFAULT = 3;
  
  private static final String PREF_KEY_NAMES = "NodeTemplateListDialog.nodeTemplates.names";
  private static final String PREF_KEY_KEYS = "NodeTemplateListDialog.nodeTemplates.keys";
  private static final String PREF_KEY_MAX_NUMBER_OF_LIST_COLUMNS = "NodeTemplateListDialog.nodeTemplates.maxNumberOfListColumns";
  private static final String PREF_KEY_AUTO_TAG_SELECTION = "NodeTemplateListDialog.nodeTemplates.autoTagSelection";
  private static final String PREF_KEY_AUTO_TAG_SELECTION_AUTO_ENABLE = "NodeTemplateListDialog.nodeTemplates.autoTagSelectionAutoEnable";
  private static final String PREF_KEY_TAG_SELECTION = "NodeTemplateListDialog.nodeTemplates.tagSelection";
  private static final String PREF_KEY_SELECTION_AUTO_OFF = "NodeTemplateListDialog.nodeTemplates.useOnSelectionAutoOff";
  
  private static final String SEPARATOR_NAME = ";;;";
  private static final String SEPARATOR_ICON = "###";
  private static final String SEPARATOR_WAY = "***";

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
  private final JCheckBoxMenuItem notForNodes;

  private final SideButton btnAdd;
  private final SideButton btnEdit;
  private final SideButton btnCopy;
  private final SideButton btnPaste;
  private final SideButton btnDelete;

  private final JPopupMenu popupMenu = new JPopupMenu();
  private final JMenu importMenu = new JMenu(tr("Import node template from preset"));
  private final JMenu setIconMenu = new JMenu(tr("Set icon for selected node template from preset"));
  
  private PreferenceChangedListener prefListener;
  private PreferenceChangedListener prefListener2;
  
  private int timer;
  private Thread autoOffTimer;
  private JLabel autoOffLabel;
  private boolean noAutoOffTimer;
  private KeyPressReleaseListener keyListener;
  private ActionListener autoTagActionListener;
  
  private boolean autoTagDisabled;
  
  private JPopupMenu prefMenu;
  
  private final AbstractAction sortItem = new AbstractAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      if(nodeList != null) {
        NodeTemplate selected = getSelectedTemplate();
        
        ArrayList<NodeTemplate> nodeTemplateList = new ArrayList<NodeTemplate>();
        
        for(DefaultListModel<NodeTemplate> m : models) {
          for(int i = 0; i < m.size(); i++) {
            nodeTemplateList.add(m.get(i));
          }
          
          m.removeAllElements();
        }
        
        Collections.sort(nodeTemplateList, NodeTemplate.COMPARATOR);
        
        for(Object el : nodeTemplateList) {
          model.addElement((NodeTemplate)el);
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
    
    JList<NodeTemplate >nodeList2 = new JList<>(model2);
    nodeList2.setAlignmentX(1.0f);
    nodeList2.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    nodeList2.setCellRenderer(renderer);
    
    nodeLists.add(nodeList2);
  }

  public NodeTemplateListDialog() {
    super(tr("Node Template List"), "nodes", tr("Store node tags for recration of nodes with the same tags"),
        Shortcut.registerShortcut("NodeTemplateList.nodetemplatelist", tr("Windows: {0}", tr("Node Template List")),
            KeyEvent.VK_B, Shortcut.ALT_CTRL_SHIFT), 150, true);
    importMenu.setIcon(ImageProvider.get("download", ImageSizes.SMALLICON));
    
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
    
    model = new DefaultListModel<>();
    models.add(model);
    
    nodeList = new JList<>(model);
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
    
    forWays = new JCheckBoxMenuItem(tr("Allow usage for ways on selection"), ImageProvider.get(OsmPrimitiveType.WAY));
    forWays.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        NodeTemplate t = getSelectedTemplate();
        
        t.setForWays(!t.isForWays());
      }
    });
    
    notForNodes = new JCheckBoxMenuItem(tr("Not for nodes"), ImageProvider.get("not_for_nodes", ImageProvider.ImageSizes.MENU));
    notForNodes.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        NodeTemplate t = getSelectedTemplate();
        
        t.setNotForNodes(!t.isNotForNodes());
        
        updateBtnEnabledState();
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
        updateBtnEnabledState();
        
        clickCount = e.getClickCount();
        
        if(clickThread == null || !clickThread.isAlive()) {
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
                  if((knownClickCount.get() == 1 || knownClickCount.get() == 4 || knownClickCount.get() == 5) && Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION, true)) {
                    if(isNodeTemplateSelected()) {
                      noAutoOffTimer = knownClickCount.get() == 5 || (((e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) == KeyEvent.ALT_DOWN_MASK) && ((e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) == KeyEvent.SHIFT_DOWN_MASK));
                      
                      handleSelection(MainApplication.getLayerManager().getEditDataSet().getSelected(), Config.getPref().getBoolean(PREF_KEY_TAG_SELECTION, true) && !noAutoOffTimer);
                      
                      if(knownClickCount.get() == 4 || knownClickCount.get() == 5 || (e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) == KeyEvent.ALT_DOWN_MASK || Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_ENABLE, true)) {
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
                    copy.actionPerformed(null);
                    setAutoTagSelectionSelected(false);
                  }
                  else if(knownClickCount.get() == 3) {
                    copy.actionPerformed(null);
                    paste.actionPerformed(new ActionEvent(btnPaste, 0, "PASTE"));
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
        if(e.getSource() instanceof JList) {
          int index = ((JList<?>)e.getSource()).locationToIndex(e.getPoint());
          
          if(e.isPopupTrigger()) {
            if(index >= 0 && ((JList<?>)e.getSource()).getCellBounds(index, index).contains(e.getPoint())) {
              ((JList<?>)e.getSource()).setSelectedIndex(index);
              
              if(Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_ENABLE, true)) {
                autoTagSelection.setEnabled(true);
                setAutoTagSelectionSelected(true);
              }
            }
            else {
              ((JList<?>)e.getSource()).clearSelection();
            }
            
            updateImportMenu();
            updateBtnEnabledState();
            
            forWays.setEnabled(((JList<?>)e.getSource()).getSelectedIndex() != -1);
            notForNodes.setEnabled(((JList<?>)e.getSource()).getSelectedIndex() != -1);
            
            NodeTemplate selected = getSelectedTemplate();
            
            if(selected != null) {
              forWays.setSelected(selected.isForWays());
              notForNodes.setSelected(selected.isNotForNodes());
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
      
      if(!isNodeTemplateSelected()) {
        setAutoTagSelectionSelected(false);
        autoTagSelection.setEnabled(false);
      }
      else {
        autoTagSelection.setEnabled(true);
      }
    };
    
    for(JList<NodeTemplate> l : nodeLists) {
      l.addMouseListener(mouseListener);
      l.addListSelectionListener(selectionListener);
    }

    autoTagActionListener = e -> {
      if(autoTagSelection.isSelected()) {
        if(e != null) {
          noAutoOffTimer = ((e.getModifiers() & ActionEvent.ALT_MASK) == ActionEvent.ALT_MASK) && ((e.getModifiers() & ActionEvent.SHIFT_MASK) == ActionEvent.SHIFT_MASK);
        }
        
        MainApplication.getMap().keyDetector.addKeyListener(keyListener);
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
      
      titleBar.invalidate();
    };
    
    p = new JPanel(new GridBagLayout());
    
    JScrollPane west = new JScrollPane(nodeList);
    west.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    
    int column = 1;
    
    p.add(west, GBC.std(column++, 1).fill());
    
    for(int i = 1; i < nodeLists.size(); i++) {
      JSeparator middle = new JSeparator(JSeparator.VERTICAL);
      
      JScrollPane east = new JScrollPane(nodeLists.get(i));
      east.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      
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
            
            item = new JCheckBoxMenuItem(tr("Auto tag objects that are selected from template"));
            item.setSelected(Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION, true));
            item.addActionListener(a -> Config.getPref().putBoolean(PREF_KEY_AUTO_TAG_SELECTION, !Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION, true)));
            
            prefMenu.add(item);
            
            item = new JCheckBoxMenuItem(tr("Enable auto tagging when selecting template"));
            item.setSelected(Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_ENABLE, true));
            item.addActionListener(a -> Config.getPref().putBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_ENABLE, !Config.getPref().getBoolean(PREF_KEY_AUTO_TAG_SELECTION_AUTO_ENABLE, true)));
            
            prefMenu.add(item);
            
            
            JMenu autoOff = new JMenu(tr("Auto off timer for auto tagging"));
            
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
        if(disableAutoTagging.isEvent(e)) {
          setAutoTagSelectionSelected(false);
          MainApplication.getMap().keyDetector.removeKeyListener(this);
        }
      }

      @Override
      public void doKeyReleased(KeyEvent e) {}
    };
    
    load();
  }
  
  private void setAutoTagSelectionSelected(boolean value) {
    autoTagSelection.setSelected(value);
    
    autoTagActionListener.actionPerformed(null);
  }
    
  @Override
  public void showDialog() {
    super.showDialog();
    
    SwingUtilities.invokeLater(() -> refillLists(true));
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
  
  private void checkStartTimer() {
    timer = Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10) * 1000 + 500;
    
    if(!noAutoOffTimer && timer > 500 && (autoOffTimer == null || !autoOffTimer.isAlive())) {
      autoOffTimer = new Thread() {
        public void run() {
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
            
            if(!noAutoOffTimer && Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10) != 0) {
              setAutoTagSelectionSelected(false);
            }
          });
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
  }
  
  private synchronized void refillLists() {
    refillLists(false);
  }
  
  private synchronized void refillLists(boolean force) {
    if(force || panelBounds == null || !panelBounds.equals(p.getBounds())) {
      int numberOfLists = Config.getPref().getInt(PREF_KEY_MAX_NUMBER_OF_LIST_COLUMNS, MAX_LIST_COLUMNS_NUMBER_DEFAULT);
      
      if(numberOfLists > 1) {
        int entryCount = 0;
        
        for(DefaultListModel<NodeTemplate> m : models) {
          entryCount += m.size();
        }
        
        int split = nodeList.getVisibleRowCount();
        
        Rectangle a = nodeList.getVisibleRect();
        
        if(model.size() > 0) {      
          split = a.height/nodeList.getCellBounds(0, 0).height;
        }
        
        if(split != 0) {
          LinkedList<NodeTemplate> entries = new LinkedList<>();
          
          for(int k = 0; k < models.size(); k++) {
            for(int i = 0; i < models.get(k).size(); i++) {
              entries.add(models.get(k).get(i));
            }
            
            models.get(k).clear();
          }
          
          int neededLists = (int)Math.ceil(entryCount / (float)split);
          int n = split;
          
          if(entryCount >= split*numberOfLists) {
            n = entryCount / numberOfLists;
            
            if(entryCount % numberOfLists != 0) {
              n++;
            }
          }
          
          int count = 0;
          int index = 0;
          
          DefaultListModel<NodeTemplate> m = models.get(index);
          
          for(NodeTemplate t : entries) {
            if(++count > n) {
              m = models.get(++index);
              count = 1;
            }
            
            m.addElement(t);
          }
          
          count = 0;
          
          for(int i = 0; i < p.getComponentCount(); i++) {
            if(count >= Math.min(neededLists, numberOfLists)) {
              p.getComponent(i).setVisible(false);
            }
            else {
              p.getComponent(i).setVisible(true);
            }
              
            if(p.getComponent(i) instanceof JScrollPane) {
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
        n.iconName = iconName;
        n.loadIcon();
        
        for(JList<NodeTemplate> list : nodeLists) {
          list.repaint();
        }
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

  private void handleSelection(Collection<OsmPrimitive> selection, boolean selectionTag) {
    if(selectionTag) {
      setAutoTagSelectionSelected(false);
      timer = 0;
    }
    
    if(((autoTagSelection.isVisible() && autoTagSelection.isSelected()) || selectionTag) && !autoTagDisabled) {
      if(!selection.isEmpty()) {
        timer = Config.getPref().getInt(PREF_KEY_SELECTION_AUTO_OFF, 10) * 1000 + 500;
        
        selection.forEach(p -> {
          NodeTemplate t = getSelectedTemplate();
          
          if(t != null) {
            boolean forWays = t.isForWays();
            boolean notForNodes = t.isNotForNodes();
            
            final ArrayList<Command> cmds = new ArrayList<>();
            
            t.map.forEach((key, value) -> {
              if(((p instanceof Node && !notForNodes) || (forWays && p instanceof Way)) && !p.hasTag(key, value)) {
                cmds.add(new ChangePropertyCommand(p, key, value));
              }
            });
            
            if(!cmds.isEmpty()) {
              UndoRedoHandler.getInstance().add(new SequenceCommand("add template to selection", cmds));
            }
          }
        });
      }
    }
  }
  
  @Override
  public synchronized void selectionChanged(SelectionChangeEvent event) {
    add.updateEnabledState();
    
    handleSelection(event.getSelection(), false);
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
    Config.getPref().addKeyPreferenceChangeListener(PREF_KEY_MAX_NUMBER_OF_LIST_COLUMNS, prefListener);
    Config.getPref().addKeyPreferenceChangeListener(PREF_KEY_AUTO_TAG_SELECTION, prefListener2);
    updateBtnEnabledState();
  }

  @Override
  public synchronized void hideNotify() {
    SelectionEventManager.getInstance().removeSelectionListener(this);
    MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
    Config.getPref().removeKeyPreferenceChangeListener(PREF_KEY_MAX_NUMBER_OF_LIST_COLUMNS, prefListener);
    Config.getPref().removeKeyPreferenceChangeListener(PREF_KEY_AUTO_TAG_SELECTION, prefListener2);
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
    if(isDocked) {
      super.collapse();
    }
    
    revalidate();
  }
  
  private void save() {
    final ArrayList<Map<String, String>> keysList = new ArrayList<>();
    final ArrayList<String> namesList = new ArrayList<>();

    for(DefaultListModel<NodeTemplate> m : models) {
      saveModel(m, keysList, namesList);
    }
    
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
            addNodeTemplate(new NodeTemplate(node));
          }
        }
        
        final Collection<Way> wayList = OsmDataManager.getInstance().getActiveDataSet().getSelectedWays();
        
        for(Way w : wayList) {
          if(!w.getKeys().isEmpty()) {
            addNodeTemplate(new NodeTemplate(w));
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
      setEnabled(ds != null && !ds.isLocked() && !Utils.isEmpty(OsmDataManager.getInstance().getInProgressSelection())
          && (!ds.getSelectedNodes().isEmpty() || !ds.getSelectedWays().isEmpty()) && nodeList.isEnabled());
    }
  }
  
  class EditAction extends JosmAction {
    EditAction() {
      super(tr("Edit name of selected node template"), /* ICON() */ "dialogs/edit", tr("Edit name of selected node template"), /* Shortcut */ null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      NodeTemplate entry = getSelectedTemplate();
      
      String result = JOptionPane.showInputDialog(MainApplication.getMainFrame(), tr("Name:"), entry.toString());

      if (result != null && !result.isBlank()) {
        entry.name = result;
        
        repaintSelectedRow();
      }
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(isNodeTemplateUsable());
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
        NodeTemplate template = getSelectedTemplate();
        
        if(template != null) {
          ClipboardUtils.copy(new PrimitiveTransferable(PrimitiveTransferData.getDataWithReferences(Collections.singleton(template.createNode()))));
        }
      }
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(isNodeTemplateUsable() && !isSelectedTemplateNotForNodes());
    }
  }

  class PasteAction extends AbstractPasteAction {
    PasteAction() {
      super(tr("Create new node from selected template and past it directly into the map view"), /* ICON() */ "paste", tr("Create new node from selected template and past it directly into the map view"), /* Shortcut */ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      if(isEnabled()) {
        NodeTemplate t = getSelectedTemplate();
        
        if(t != null) {
          transferHandler.pasteOn(getLayerManager().getEditLayer(), MainApplication.getMap().mapView.getCenter(), new PrimitiveTransferable(PrimitiveTransferData.getDataWithReferences(Collections.singleton(t.createNode()))));
        }
      }
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(isNodeTemplateUsable() && !isSelectedTemplateNotForNodes());
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
          models.get(i).remove(index);
          
          if(models.get(i).size() >= index) {
            nodeLists.get(i).setSelectedIndex(index-1);
          }
          
          break;
        }
      }
      
      refillLists(true);
      updateBtnEnabledState();
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(isNodeTemplateUsable());
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

    public NodeTemplate(String iconName, OsmPrimitive p) {
      map = new LinkedHashMap<>();
      
      p.getKeys().forEach((key,value) -> {
        if(!value.isBlank()) {
          map.put(key, value);
        }
      });
      
      this.iconName = iconName;
      loadName(p);
      loadIcon();
    }
    
    public NodeTemplate(Node node) {
      map = node.getKeys();
      loadName(node);
    }
    
    public NodeTemplate(Way way) {
      map = way.getKeys();
      loadName(way);
      forWays = true;
      notForNodes = true;
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
    
    private void loadName(OsmPrimitive p) {
      name = p.getLocalName();

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
