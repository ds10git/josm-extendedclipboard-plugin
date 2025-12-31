package org.openstreetmap.josm.plugins.extendedclipboard.nodetemplatelist;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.gui.datatransfer.OsmTransferHandler;
import org.openstreetmap.josm.gui.datatransfer.PrimitiveTransferable;
import org.openstreetmap.josm.gui.datatransfer.data.PrimitiveTransferData;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;

public class NodeTemplateList {
  public static final NodeTemplate SEPARATOR = createSeparator();
  
  private static final String PREF_KEY_IDS = "NodeTemplateListDialog.nodeTemplates.ids";
  private static final String PREF_KEY_NAMES = "NodeTemplateListDialog.nodeTemplates.names";
  private static final String PREF_KEY_KEYS = "NodeTemplateListDialog.nodeTemplates.keys";
  private static final String PREF_KEY_CTRL_KEYS = "NodeTemplateListDialog.nodeTemplates.keysCtrl";
  private static final String PREF_KEY_SHIFT_KEYS = "NodeTemplateListDialog.nodeTemplates.keysShift";
  
  private static final String SEPARATOR_NAME = ";;;";
  private static final String SEPARATOR_ICON = "###";
  private static final String SEPARATOR_WAY = "***";

  private static NodeTemplateList instance;
  private final List<NodeTemplate> list;
  
  private final JMenu quickAccessMenu;
  private final OsmTransferHandler transferHandler;
  
  private final HashMap<NodeTemplate, NodeTemplateMenuItem> quickAccessMenuItemMap;
  private NodeTemplateListDialog dialog;
  
  private NodeTemplateList() {
    instance = this;
    list = new LinkedList<>();
    
    transferHandler = new OsmTransferHandler();    
    quickAccessMenuItemMap = new HashMap<>();
    
    quickAccessMenu = new JMenu(tr("Node Template List"));
    quickAccessMenu.setIcon(ImageProvider.get("dialogs/nodes"));
    
    MainApplication.getMenu().presetsMenu.add(quickAccessMenu,3);
    MainApplication.getMenu().presetsMenu.insertSeparator(3);
    
    load();
  }
  
  public void setDialog(NodeTemplateListDialog dialog) {
    this.dialog = dialog;
  }
  
  public static synchronized void initialize() {
    if(instance == null) {
      new NodeTemplateList();
    }
  }
  
  public static synchronized NodeTemplateList get() {
    if(instance != null) {
      initialize();
    }
    
    return instance;
  }
  
  private static NodeTemplate createSeparator() {
    NodeTemplate separator = new NodeTemplate("S-E-P-A-R-A-T-O-R");
    separator.setForWays(false);
    separator.setForClosedWays(false);
    separator.setNotForNodes(true);
    
    return separator;
  }
  
  void add(NodeTemplate t) {
    list.add(t);
    addQuickAccess(t);
  }
  
  void fillModel(DefaultListModel<NodeTemplate> model) {
    model.addAll(list);
  }
  
  void updateQuickAccess(NodeTemplate t) {
    if(quickAccessMenuItemMap.containsKey(t)) {
      updateQuickMenuItemIcon(t);
    }
    else {
      addQuickAccess(t);
      rebuildQuickAccessMenu();
    }
  }
  
  private void addQuickAccess(NodeTemplate t) {
    if(t.getIconName() != null) {
      NodeTemplateMenuItem item = new NodeTemplateMenuItem(t, this);
      quickAccessMenu.add(item);
      quickAccessMenuItemMap.put(t, item);
    }
  }
  
  void nodeTemplateDeleted(NodeTemplate t) {
    list.remove(t);
    NodeTemplateMenuItem item = quickAccessMenuItemMap.remove(t);
    
    if(item != null) {
      quickAccessMenu.remove(item);
      
      if(MainApplication.getToolbar() != null && MainApplication.getToolbar().unregister(item.getAction()) != null) {
        MainApplication.getToolbar().refreshToolbarControl();
      }
    }
  }
  
  private boolean isItemEnabled(NodeTemplateMenuItem item, Collection<OsmPrimitive> selection) {
    return !item.t.isNotForNodes() || ((item.t.isForWays() && selection.stream().filter(w -> w instanceof Way && !((Way)w).isClosed()).count() > 0 || item.t.isForClosedWays() && selection.stream().filter(w -> w instanceof Way && ((Way)w).isClosed()).count() > 0) && (!item.t.isOnlyForUntaggedObjects() || selection.stream().filter(p -> !p.hasKeys()).count() > 0));
  }
  
  private boolean isItemEabledForNodes(NodeTemplateMenuItem item, Collection<OsmPrimitive> selection) {
    return item.t.isNotForNodes() || (!item.t.isNotForNodes() && selection.stream().filter(p -> p instanceof Node).count() > 0);
  }
  
  void updateQuickMenuItemsEnabledState() {
    if(OsmDataManager.getInstance().getActiveDataSet() != null) {
      Collection<OsmPrimitive> selection = OsmDataManager.getInstance().getActiveDataSet().getSelected();
      
      quickAccessMenuItemMap.forEach((k,item) -> {      
        item.getAction().setEnabled(isItemEnabled(item, selection));
        item.setEnabled(item.getAction().isEnabled() && isItemEabledForNodes(item, selection));
      });
    }
  }
  
  void updateQuickMenuItemIcon(NodeTemplate t) {
    NodeTemplateMenuItem item = quickAccessMenuItemMap.get(t);
    
    if(item != null) {
      Collection<OsmPrimitive> selection = OsmDataManager.getInstance().getActiveDataSet().getSelected();
      
      t.addIconToAction(item.getAction(), isItemEnabled(item, selection));
      item.setEnabled(item.getAction().isEnabled() && isItemEabledForNodes(item, selection));
    }
  }
  
  void setList(List<NodeTemplate> list) {
    this.list.clear();
    this.list.addAll(list);
    
    rebuildQuickAccessMenu();
  }
  
  void updateList(List<DefaultListModel<NodeTemplate>> models) {
    list.clear();
    
    for(DefaultListModel<NodeTemplate> model : models) {
      for(int i = 0; i < model.size(); i++) {
        list.add(model.get(i));
      }
    }
    
    rebuildQuickAccessMenu();
  }
  
  private void rebuildQuickAccessMenu() {
    quickAccessMenu.removeAll();
    
    for(NodeTemplate t : list) {
      if(t == SEPARATOR) {
        quickAccessMenu.addSeparator();
      }
      else if(quickAccessMenuItemMap.containsKey(t)) {
        quickAccessMenu.add(quickAccessMenuItemMap.get(t));
      }
    }    
  }
  
  void updateQuickMenuEntry(NodeTemplate t) {
    NodeTemplateMenuItem item = quickAccessMenuItemMap.get(t);
    
    if(item != null) {
      item.setText(item.getName());
      item.getAction().putValue(Action.NAME, t.toString());
      item.getAction().putValue(TaggingPreset.OPTIONAL_TOOLTIP_TEXT, t.toString());
      
      if(MainApplication.getToolbar() != null) {
        JToolBar toolbar = MainApplication.getToolbar().control;
      
        for(int i = 0; i < toolbar.getComponentCount(); i++) {
          if(toolbar.getComponent(i) instanceof AbstractButton && Objects.equals(((AbstractButton)toolbar.getComponent(i)).getAction().getValue("toolbar"), NodeTemplate.class.getSimpleName()+"."+t.getId())) {
            ((AbstractButton)toolbar.getComponent(i)).setToolTipText(t.toString());
            break;
          }
        }
      }
    }
  }
  
  private void load() {    
    List<String> ids = Config.getPref().getList(PREF_KEY_IDS, null);
    List<String> names = Config.getPref().getList(PREF_KEY_NAMES, null);
    List<Map<String, String>> keys = Config.getPref().getListOfMaps(PREF_KEY_KEYS, null);
    List<Map<String, String>> ctrl = Config.getPref().getListOfMaps(PREF_KEY_CTRL_KEYS, null);
    List<Map<String, String>> shift = Config.getPref().getListOfMaps(PREF_KEY_SHIFT_KEYS, null);
    
    if (names != null && keys != null) {
      int n = Math.min(names.size(), keys.size());

      for (int i = 0; i < n; i++) {
        String id = ids != null ? ids.get(i) : null;
        String name = names.get(i);
        String iconName = null;
        boolean forWays = false;
        boolean forClosedWays = false;
        boolean notForNodes = false;
        boolean onlyForUntaggedObjects = false;

        if(name.contains(SEPARATOR_ICON)) {
          iconName = name.substring(name.indexOf(SEPARATOR_ICON)+SEPARATOR_ICON.length());
          name = name.substring(0,name.indexOf(SEPARATOR_ICON));
        }
        
        if(name.contains(SEPARATOR_WAY)) {
          String[] values = name.split(SEPARATOR_WAY.replace("*", "\\*"), -1);
          name = values[values.length-1];
          
          if(values.length >= 2) {
            notForNodes = Boolean.parseBoolean(values[values.length-2]);
          }
          if(values.length >= 3) {
            forWays = Boolean.parseBoolean(values[values.length-3]);
          }
          if(values.length >= 4) {
            onlyForUntaggedObjects = Boolean.parseBoolean(values[values.length-4]);
          }
          if(values.length >= 5) {
            forClosedWays = Boolean.parseBoolean(values[values.length-5]);
          }
        }
        
        if (name.contains(SEPARATOR_NAME)) {
          name = name.substring(0, name.indexOf(SEPARATOR_NAME));
        }
        
        if(Objects.equals(SEPARATOR.toString(), name)) {
          list.add(SEPARATOR);
          quickAccessMenu.addSeparator();
        }
        else {
          NodeTemplate t = new NodeTemplate(id, name, iconName, keys.get(i), forWays, forClosedWays, notForNodes, onlyForUntaggedObjects);
          
          if(ctrl != null) {
            t.setCtrl(ctrl.get(i));
            t.setShift(shift.get(i));
          }
          
          list.add(t);
          addQuickAccess(t);
        }
      }
    }
    else {
      final String[] nameArr = {tr("Tree"), tr("Tree Row"), tr("Waste Basket"), tr("Bench"),  tr("Hedge")};
      final String[] iconArr = {"presets/landmark/trees_broad_leaved.svg", "presets/landmark/tree_row.svg", "presets/service/recycling/waste_basket.svg", "presets/leisure/bench.svg", "presets/barrier/hedge.svg"};
      final Tag[] tags = {new Tag("natural", "tree"), new Tag("natural", "tree_row"), new Tag("amenity", "waste_basket"), new Tag("amenity", "bench"), new Tag("barrier", "hedge")};
      final boolean[] forWays = {false, true, false, false, true};
      final boolean[] forClosedWays = {false, false, false, false, true};
      
      for(int i = 0; i < tags.length; i++) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(tags[i].getKey(), tags[i].getValue());
        
        NodeTemplate t = new NodeTemplate(null, nameArr[i], iconArr[i], map, forWays[i], forClosedWays[i], forWays[i], false);
        list.add(t);
        addQuickAccess(t);
      }
    }
  }
  
  public void save() {
    final ArrayList<Map<String, String>> keysList = new ArrayList<>();
    final ArrayList<Map<String, String>> ctrlList = new ArrayList<>();
    final ArrayList<Map<String, String>> shiftList = new ArrayList<>();
    
    final ArrayList<String> namesList = new ArrayList<>();
    final ArrayList<String> idsList = new ArrayList<>();

    for (int i = 0; i < list.size(); i++) {
      NodeTemplate template = list.get(i);

      String id = template.getId();
      String name = template.toString();
      int count = 1;

      while (namesList.contains(name)) {
        name = template.toString() + SEPARATOR_NAME + count++;
      }
      
      name = String.valueOf(template.isNotForNodes()) + SEPARATOR_WAY + name;
      name = String.valueOf(template.isForWays()) + SEPARATOR_WAY + name;
      name = String.valueOf(template.isOnlyForUntaggedObjects()) + SEPARATOR_WAY + name;
      name = String.valueOf(template.isForClosedWays()) + SEPARATOR_WAY + name;
      
      if(template.getIconName() != null) {
        name += SEPARATOR_ICON + template.getIconName();
      }
      
      idsList.add(id);
      namesList.add(name);
      
      template.saveMaps(keysList, ctrlList, shiftList);
    }
    
    Config.getPref().putList(PREF_KEY_IDS, idsList);
    Config.getPref().putList(PREF_KEY_NAMES, namesList);
    Config.getPref().putListOfMaps(PREF_KEY_KEYS, keysList);
    Config.getPref().putListOfMaps(PREF_KEY_CTRL_KEYS, ctrlList);
    Config.getPref().putListOfMaps(PREF_KEY_SHIFT_KEYS, shiftList);
  }
  
  void copy(NodeTemplate t, ActionEvent e) {
    if(t != null && !t.isNotForNodes()) {
      ClipboardUtils.copy(new PrimitiveTransferable(PrimitiveTransferData.getDataWithReferences(Collections.singleton(t.createNode((e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK, (e.getModifiers() & ActionEvent.SHIFT_MASK) == ActionEvent.SHIFT_MASK)))));
    }
  }
  
  void paste(NodeTemplate t, ActionEvent e) {
    if(t != null && !t.isNotForNodes()) {
      PrimitiveTransferable node = new PrimitiveTransferable(PrimitiveTransferData.getDataWithReferences(Collections.singleton(t.createNode((e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK, (e.getModifiers() & ActionEvent.SHIFT_MASK) == ActionEvent.SHIFT_MASK))));
      ClipboardUtils.copy(node);
      
      transferHandler.pasteOn(MainApplication.getLayerManager().getEditLayer(), MainApplication.getMap().mapView.getCenter(), node);
    }
  }
  
  private Thread menuTemplateThread;
  private int menuTemplateClickCount;
  private long menuTemplateLastClick;
  
  private synchronized void menuTemplateSelected(ActionEvent e, NodeTemplate t) {
    menuTemplateLastClick = System.currentTimeMillis();
    menuTemplateClickCount++;
    
    if(menuTemplateThread == null || !menuTemplateThread.isAlive()) {
      menuTemplateThread = new Thread() {
        @Override
        public void run() {
          while(System.currentTimeMillis()-menuTemplateLastClick < 200) {
            try {
              Thread.sleep(100);
            } catch (InterruptedException e) {}
          }
          
          SwingUtilities.invokeLater(() -> { 
            if(menuTemplateClickCount == 1 && dialog != null) {
              dialog.handleSelection(t, OsmDataManager.getInstance().getActiveDataSet().getSelected(),  Config.getPref().getBoolean(NodeTemplateListDialog.PREF_KEY_TAG_SELECTION, true), ((e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK), ((e.getModifiers() & ActionEvent.SHIFT_MASK) == ActionEvent.SHIFT_MASK), false);
            }
            else if(menuTemplateClickCount == 2) {
              copy(t, e);
            }
            else if(menuTemplateClickCount == 3) {
              paste(t, e);
            }
            
            menuTemplateClickCount = 0;
          });
        }
      };
      menuTemplateThread.start();
    }
  }
  
  static final class NodeTemplateMenuItem extends JMenuItem {
    private NodeTemplate t;
    
    public NodeTemplateMenuItem(NodeTemplate t, NodeTemplateList l) {
      super(t.toString(), t.getIcon());
      this.t = t;
      
      Action a = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          l.menuTemplateSelected(e, t);
        }
      };
      
      a.putValue("toolbar", NodeTemplate.class.getSimpleName()+"."+t.getId());
      a.putValue(Action.NAME, t.toString());
      a.putValue(TaggingPreset.OPTIONAL_TOOLTIP_TEXT, t.toString());
      t.addIconToAction(a, false);
      
      setAction(a);
      
      if(MainApplication.getToolbar() != null) {
        MainApplication.getToolbar().register(a);
      }
    }
    
    @Override
    public String getText() {
      return t != null ? t.toString() : super.getText();
    }
    
    @Override
    public Icon getIcon() {
      return t != null ? t.getIcon() : super.getIcon();
    }
  }
}
