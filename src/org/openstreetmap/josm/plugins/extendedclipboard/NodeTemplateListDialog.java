// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.extendedclipboard;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.gui.datatransfer.PrimitiveTransferable;
import org.openstreetmap.josm.gui.datatransfer.data.PrimitiveTransferData;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

public class NodeTemplateListDialog extends ToggleDialog implements DataSelectionListener {
  private static final String PREF_KEY_NAMES = "NodeTemplateListDialog.nodeTemplates.names";
  private static final String PREF_KEY_KEYS = "NodeTemplateListDialog.nodeTemplates.keys";
  private static final String SEPARATOR_NAME = ";;;";

  private final JList<NodeTemplate> nodeList;
  private final DefaultListModel<NodeTemplate> model;

  private final AddAction add = new AddAction();
  private final EditAction edit = new EditAction();
  private final CopyAction copy = new CopyAction();
  private final DeleteAction delete = new DeleteAction();

  private final SideButton btnAdd = new SideButton(add);
  private final SideButton btnEdit = new SideButton(edit);
  private final SideButton btnCopy = new SideButton(copy);
  private final SideButton btnDelete = new SideButton(delete);

  private final JPopupMenu popupMenu = new JPopupMenu();

  public NodeTemplateListDialog() {
    super(tr("Node Template List"), "nodes", tr("Store node tags for recration of nodes with the same tags"),
        Shortcut.registerShortcut("NodeTemplateList.nodetemplatelist", tr("Windows: {0}", tr("Node Template List")),
            KeyEvent.VK_B, Shortcut.ALT_CTRL_SHIFT), 150, true);

    model = new DefaultListModel<>();
    nodeList = new JList<>(model);
    nodeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    createPopupMenu();
    
    nodeList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        handlePopupMenu(e);
      }
      
      @Override
      public void mousePressed(MouseEvent e) {
        handlePopupMenu(e);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        updateBtnEnabledState();

        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
          copy.actionPerformed(null);
          MainApplication.getMainPanel().requestFocusInWindow();
        }
      }
      
      private void handlePopupMenu(MouseEvent e) {
        int index = nodeList.locationToIndex(e.getPoint());
        
        if(e.isPopupTrigger()) {
          if(index >= 0 && nodeList.getCellBounds(index, index).contains(e.getPoint())) {
            nodeList.setSelectedIndex(index);
          }
          else {
            nodeList.clearSelection();
          }
          
          updateBtnEnabledState();
          popupMenu.show(e.getComponent(), e.getPoint().x, e.getPoint().y);
        }
      }
    });

    Component c = createLayout(nodeList, true, Arrays.asList(this.btnAdd, this.btnCopy, this.btnEdit, this.btnDelete));
    nodeList.setSize(c.getSize());
    load();
  }
  
  private void createPopupMenu() {
    popupMenu.add(MenuUtils.createJMenuItemFrom(copy));
    popupMenu.add(MenuUtils.createJMenuItemFrom(edit));
    popupMenu.add(MenuUtils.createJMenuItemFrom(delete));
  }

  @Override
  public void selectionChanged(SelectionChangeEvent event) {
    add.updateEnabledState();
  }

  private void updateBtnEnabledState() {
    add.updateEnabledState();
    edit.updateEnabledState();
    copy.updateEnabledState();
    delete.updateEnabledState();
  }

  private void repaintRow(int index) {
    if (index >= 0) {
      nodeList.repaint(nodeList.getCellBounds(index, index));
    }
  }

  @Override
  public void showNotify() {
    SelectionEventManager.getInstance().addSelectionListenerForEdt(this);
    updateBtnEnabledState();
  }

  @Override
  public synchronized void hideNotify() {
    SelectionEventManager.getInstance().removeSelectionListener(this);
  }

  @Override
  public void destroy() {
    save();
    super.destroy();
  }

  private void load() {
    List<String> names = Config.getPref().getList(PREF_KEY_NAMES);
    List<Map<String, String>> keys = Config.getPref().getListOfMaps(PREF_KEY_KEYS);

    if (names != null && keys != null) {
      int n = Math.min(names.size(), keys.size());

      for (int i = 0; i < n; i++) {
        String name = names.get(i);

        if (name.contains(SEPARATOR_NAME)) {
          name = name.substring(0, name.indexOf(SEPARATOR_NAME));
        }

        model.addElement(new NodeTemplate(name, keys.get(i)));
      }
    }
  }

  private void save() {
    final ArrayList<Map<String, String>> keysList = new ArrayList<>();
    final ArrayList<String> namesList = new ArrayList<>();

    for (int i = 0; i < model.size(); i++) {
      NodeTemplate template = model.get(i);

      String name = template.name;
      int count = 1;

      while (namesList.contains(name)) {
        name = template.name + SEPARATOR_NAME + count++;
      }

      namesList.add(name);

      HashMap<String, String> keysMap = new HashMap<>();

      template.map.forEach((key, value) -> {
        keysMap.put(key, value);
      });

      keysList.add(keysMap);
    }

    Config.getPref().putList(PREF_KEY_NAMES, namesList);
    Config.getPref().putListOfMaps(PREF_KEY_KEYS, keysList);
  }

  class AddAction extends JosmAction {
    AtomicBoolean isPerforming = new AtomicBoolean(false);

    AddAction() {
      super(null, /* ICON() */ "dialogs/add", tr("Create new node templates from selected nodes"),
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
            model.addElement(new NodeTemplate(node));
          }
        }
      } finally {
        isPerforming.set(false);
      }
    }

    @Override
    protected final void updateEnabledState() {
      DataSet ds = OsmDataManager.getInstance().getActiveDataSet();
      setEnabled(ds != null && !ds.isLocked() && !Utils.isEmpty(OsmDataManager.getInstance().getInProgressSelection())
          && !ds.getSelectedNodes().isEmpty() && model != null);
    }
  }

  class EditAction extends JosmAction {
    EditAction() {
      super(null, /* ICON() */ "dialogs/edit", tr("Edit name of selected node template"), /* Shortcut */ null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      NodeTemplate entry = nodeList.getSelectedValue();
      String result = JOptionPane.showInputDialog(MainApplication.getMainFrame(), tr("Name:"), entry.toString());

      if (result != null && !result.isBlank()) {
        entry.name = result;
        repaintRow(nodeList.getSelectedIndex());
      }
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(nodeList != null && nodeList.getSelectedIndex() >= 0);
    }
  }

  class CopyAction extends JosmAction {
    CopyAction() {
      super(null, /* ICON() */ "copy", tr("Copy new node from selected template to JOSM clipboard"),
          /* Shortcut */ null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      ClipboardUtils.copy(new PrimitiveTransferable(PrimitiveTransferData
          .getDataWithReferences(Collections.singleton(nodeList.getSelectedValue().createNode()))));
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(nodeList != null && nodeList.getSelectedIndex() >= 0);
    }
  }

  class DeleteAction extends JosmAction {
    DeleteAction() {
      super(null, /* ICON() */ "dialogs/delete", tr("Delete selected node template"), /* Shortcut */ null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      model.remove(nodeList.getSelectedIndex());
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(nodeList != null && nodeList.getSelectedIndex() >= 0);
    }
  }

  private static final class NodeTemplate {
    private String name;
    private Map<String, String> map;

    public NodeTemplate(String name, Map<String, String> map) {
      this.name = name;
      this.map = map;
    }

    public NodeTemplate(Node node) {
      map = node.getKeys();
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
