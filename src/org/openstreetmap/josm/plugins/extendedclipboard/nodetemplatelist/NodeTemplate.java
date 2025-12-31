package org.openstreetmap.josm.plugins.extendedclipboard.nodetemplatelist;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.TagMap;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageResource;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;

public class NodeTemplate {
  public static final Comparator<Object> COMPARATOR = new Comparator<Object>() {
    @Override
    public int compare(Object n0, Object n1) {
      return ((NodeTemplate)n0).name.compareToIgnoreCase(((NodeTemplate)n1).name);
    }
  };
  
  private String id;
  private String name;
  private String iconName;
  private ImageIcon icon;
  private ImageIcon iconBig;
  
  private boolean forWays;
  private boolean forClosedWays;
  private boolean notForNodes;
  private boolean onlyForUntaggedObjects;
  
  private Map<String, String> map;
  private Map<String, String> ctrl;
  private Map<String, String> shift;
  
  public NodeTemplate(String id, String name, String iconName, Map<String, String> map, boolean forWays, boolean forClosedWays, boolean notForNodes, boolean onlyForUntaggedObjects) {
    this.name = name;
    this.id = id == null ? System.currentTimeMillis()+name+((int)(Math.random()*1000)) : id;
    this.map = getMapFromMap(map);
    this.iconName = iconName;
    this.forWays = forWays;
    this.forClosedWays = forClosedWays;
    this.notForNodes = notForNodes;
    this.onlyForUntaggedObjects = onlyForUntaggedObjects;
    ctrl = new LinkedHashMap<>();
    shift = new LinkedHashMap<>();
    loadIcon();
  }
  
  public Map<String,String> getMapFromMap(Map<String,String> map) {
    if(map instanceof TagMap) {
      Map<String,String> newMap = new LinkedHashMap<String, String>();
      map.forEach((key,value) -> newMap.put(key, value));
      map = newMap;
    }
    
    return map;
  }
  
  void setMap(Map<String, String> map) {
    this.map = map;
  }
  
  void setCtrl(Map<String, String> ctrl) {
    this.ctrl = ctrl;
  }
  
  void setShift(Map<String, String> shift) {
    this.shift = shift;
  }
  
  void setName(String name) {
    this.name = name;
  }
  
  void setIconName(String iconName) {
    this.iconName = iconName;
    loadIcon();
  }
  
  Map<String, String> getMap() {
    return map;
  }
  
  Map<String, String> getCtrl() {
    return ctrl;
  }
  
  Map<String, String> getShift() {
    return shift;
  }

  public NodeTemplate(String iconName, OsmPrimitive p) {
    map = new LinkedHashMap<>();
    ctrl = new LinkedHashMap<>();
    shift = new LinkedHashMap<>();
    
    p.getKeys().forEach((key,value) -> {
      if(!value.isBlank()) {
        map.put(key, value);
      }
    });
    
    this.id = String.valueOf(System.currentTimeMillis()+((int)(Math.random() * 10000)));
    this.iconName = iconName;
    loadName(p);
    loadIcon();
  }
  
  public NodeTemplate(String name) {
    this.name = name;
    this.id = System.currentTimeMillis()+name+((int)(Math.random() * 1000));
    map = new LinkedHashMap<>();
    ctrl = new LinkedHashMap<>();
    shift = new LinkedHashMap<>();
  }
  
  public NodeTemplate(Node node) {
    map = getMapFromMap(node.getKeys());
    ctrl = new LinkedHashMap<>();
    shift = new LinkedHashMap<>();
    
    loadName(node);
  }
  
  public NodeTemplate(Way way) {
    map = way.getKeys();
    loadName(way);
    forWays = !way.isClosed();
    forClosedWays = way.isClosed();
    notForNodes = true;
    ctrl = new LinkedHashMap<>();
    shift = new LinkedHashMap<>();
  }
  
  public void setOnlyForUntaggedObjects(boolean onlyForUntaggedObjects) {
    this.onlyForUntaggedObjects = onlyForUntaggedObjects;
  }
  
  public boolean isOnlyForUntaggedObjects() {
    return onlyForUntaggedObjects;
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
  
  public void setForClosedWays(boolean value) {
    forClosedWays = value;
  }
  
  public String getId() {
    return id;
  }
  
  public String getIconName() {
    return iconName;
  }
  
  public ImageIcon getIcon() {
    return icon;
  }
  
  public boolean isForClosedWays() {
    return forClosedWays;
  }
  
  public boolean isEnabled(boolean tagging) {
    return (!map.isEmpty() || !ctrl.isEmpty() || !shift.isEmpty()) && (tagging || !isNotForNodes());
  }
  
  public boolean isCompatible(OsmPrimitive p) {      
    return ((p instanceof Way && ((!((Way)p).isClosed() && forWays) || ((Way)p).isClosed() && forClosedWays)) || (p instanceof Node && !notForNodes)) && (!onlyForUntaggedObjects || onlyMatchingTags(p));
  }
  
  private boolean onlyMatchingTags(OsmPrimitive p) {
    boolean result = !p.hasKeys();
    
    if(!result) {
      if(p.getNumKeys() == map.size() || p.getNumKeys() == (map.size() + ctrl.size()) || p.getNumKeys() == (map.size() + shift.size())) {
        result = true;
        
        for(String key : map.keySet()) {
          if(!p.hasTag(key, map.get(key))) {
            result = false;
            break;
          }
        }
        
        if(result && p.getNumKeys() != map.size()) {
          boolean hasShiftTag = false;
          boolean shiftComplete = true;
          boolean hasCtrlTag = false;
          boolean ctrlComplete = true;
        
          if(!shift.isEmpty()) {
            for(String key : shift.keySet()) {
              if(p.hasTag(key, shift.get(key))) {
                hasShiftTag = true;
              }
              else {
                shiftComplete = false;
              }
            }
          }
          
          if(!ctrl.isEmpty()) {
            for(String key : ctrl.keySet()) {
              if(p.hasTag(key, ctrl.get(key))) {
                hasCtrlTag = true;
              }
              else {
                ctrlComplete = false;
              }
            }
          }
          
          result = (!hasShiftTag && hasCtrlTag && ctrlComplete && p.getNumKeys() == (map.size() + ctrl.size())) || 
              (!hasCtrlTag && hasShiftTag && shiftComplete && p.getNumKeys() == (map.size() + shift.size()));
        }
      }
    }
    
    return result;
  }
  
  public ArrayList<Command> createChangeCommand(final OsmPrimitive p, boolean ctrl, boolean shift, final AtomicBoolean found, boolean deactivateAutoTaggingIfNotCompatible) {
    ArrayList<Command> cmds = new ArrayList<>();
    found.set(found.get() || (deactivateAutoTaggingIfNotCompatible && Objects.equals(MainApplication.getMap().mapModeDraw.getValue("active"), Boolean.TRUE) && (p instanceof Node) && !p.hasKeys()));

    if(isCompatible(p)) {
      found.set(true);
      
      map.forEach((key, value) -> {
        if(!p.hasTag(key, value)) {
          cmds.add(new ChangePropertyCommand(p, key, value));
        }
      });
      
      LinkedHashMap<String, String> ctrlShiftMap = new LinkedHashMap<>();
      
      this.ctrl.forEach((key,value) -> {
        if(!p.hasTag(key, value) && ctrl && !shift) {
          ctrlShiftMap.put(key, value);
        }
        else if(p.hasTag(key, value) && shift) {
          ctrlShiftMap.put(key, null);
        }
      });
      
      this.shift.forEach((key,value) -> {
        if(!p.hasTag(key, value) && !ctrl && shift) {
          ctrlShiftMap.put(key, value);
        }
        else if(p.hasTag(key, value) && !ctrlShiftMap.containsKey(key) && ctrl) {
          ctrlShiftMap.put(key, null);
        }
      });
      
      ctrlShiftMap.forEach((key,value) -> cmds.add(new ChangePropertyCommand(p, key, value)));
    }
    
    return cmds;
  }
  
  private void internalAddIconToAction(Action a, boolean enabled) {
    a.putValue(Action.SMALL_ICON, icon);
    a.putValue(Action.LARGE_ICON_KEY, iconBig);
    a.setEnabled(enabled);
  }
  
  public void addIconToAction(Action a, boolean enabled) {
    if(wait != null && wait.isAlive()) {
      new Thread() {
        public void run() {
          while(wait.isAlive()) {
            try {
              Thread.sleep(100);
            } catch (InterruptedException e) {}
          }
          
          SwingUtilities.invokeLater(() -> {
            internalAddIconToAction(a, enabled);
          });
        };
      }.start();
    }
    else {
      internalAddIconToAction(a, enabled);
    }
  }
  
  private Thread wait;
  
  private void loadIcon() {
    if(iconName != null) {
      try {
        final TaggingPreset dummy = new TaggingPreset();
        dummy.setIcon(iconName);
        
        wait = new Thread() {
          @Override
          public void run() {
            int time = 2000;
            
            while(dummy.getValue("ImageResource") == null && (time-=100) > 0) {
              try {
                sleep(100);
              } catch (InterruptedException e) {
                // ignore intentionally
              }
            }
            
            ImageResource ir = (ImageResource)dummy.getValue("ImageResource");
            
            if(ir != null) {
              final ImageIcon i = ir.getImageIcon(ImageSizes.SMALLICON.getImageDimension());
              
              if(i != null) {
                icon = new ImageIcon() {
                  @Override
                  public int getIconWidth() {
                    return ImageSizes.SMALLICON.getAdjustedWidth();
                  }
                  
                  @Override
                  public int getIconHeight() {
                    return ImageSizes.SMALLICON.getAdjustedHeight();
                  }
                  
                  @Override
                  public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
                    x += getIconWidth()/2 - i.getIconWidth()/2;
                    y += getIconHeight()/2 - i.getIconHeight()/2;
                    
                    i.paintIcon(c, g, x, y);
                  }
                };
               
                
                final ImageIcon iBig = ir.getImageIcon(ImageSizes.LARGEICON.getImageDimension());
                final ImageIcon node = ImageProvider.get("data/node", ImageSizes.SMALLICON);
                
                iconBig = new ImageIcon() {
                  @Override
                  public int getIconWidth() {
                    return ImageSizes.LARGEICON.getAdjustedWidth();
                  }
                  
                  @Override
                  public int getIconHeight() {
                    return ImageSizes.LARGEICON.getAdjustedHeight();
                  }
                  
                  @Override
                  public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
                    int xOld = x;
                    int yOld = y;
                    
                    x += getIconWidth()/2 - iBig.getIconWidth()/2;
                    y += getIconHeight()/2 - iBig.getIconHeight()/2;
                    iBig.paintIcon(c, g, x, y);
                    
                    xOld += getIconWidth();
                    yOld += getIconHeight();
                    
                    xOld -= node.getIconWidth()*3/4;
                    yOld -= node.getIconHeight()*3/4;
                    
                    node.paintIcon(c, g, xOld, yOld);
                  }
                };

                BufferedImage iconImage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
                icon.paintIcon(null, iconImage.getGraphics(), 0, 0);
                icon = new ImageIcon(iconImage);
                
                iconImage = new BufferedImage(iconBig.getIconWidth(), iconBig.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
                iconBig.paintIcon(null, iconImage.getGraphics(), 0, 0);
                iconBig = new ImageIcon(iconImage);
              }
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
    
    id = System.currentTimeMillis()+name+((int)(Math.random() * 1000));
  }

  public Node createNode(boolean ctrl, boolean shift) {
    Node node = new Node(new EastNorth(0, 0));
    node.setKeys(map);
    
    if(ctrl && !shift) {
      node.putAll(this.ctrl);
    }
    else if(!ctrl && shift) {
      node.putAll(this.shift);
    }

    return node;
  }
  
  public void saveMaps(ArrayList<Map<String, String>> keysList, ArrayList<Map<String, String>> ctrlList, ArrayList<Map<String, String>> shiftList) {
    keysList.add(map);
    ctrlList.add(ctrl);
    shiftList.add(shift);
  }

  @Override
  public String toString() {
    return name;
  }
}
