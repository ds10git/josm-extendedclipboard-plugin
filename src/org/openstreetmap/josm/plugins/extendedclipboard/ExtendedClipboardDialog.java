// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.extendedclipboard;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

public class ExtendedClipboardDialog extends ToggleDialog implements DataSelectionListener, ActiveLayerChangeListener {
  private static final DateFormat FORMAT_DATE = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
  private static final DefaultListModel<ClipboardEntry> EMPTY_MODEL = new DefaultListModel<>();
  private static final int MAX_MODEL_SIZE = 10;
  private static final String PREF_NAMES = "extendedclipboard.pref.names";
  
  private final JList<ClipboardEntry> clipboard;
  private final Hashtable<OsmDataLayer, DefaultListModel<ClipboardEntry>> modelTable;
  private DefaultListModel<ClipboardEntry> model;
  
  private final AddAction add = new AddAction();
  private final AddNewAction addNew = new AddNewAction();
  private final RemoveAction remove = new RemoveAction();
  private final NewClipboardAction clipboardNew = new NewClipboardAction();
  private final EditAction edit = new EditAction();
  private final ReverseAction reverse = new ReverseAction();
  private final RestoreAction restore = new RestoreAction();
  private final ClearAction clear = new ClearAction();
  private final DeleteAction delete = new DeleteAction();
  
  /**
   * The Add button (needed to be able to disable it)
   */
  private final SideButton btnAdd = new SideButton(add);
  private final SideButton btnRemove = new SideButton(remove);
  private final SideButton btnNewClipboard = new SideButton(clipboardNew);
  private final SideButton btnEdit = new SideButton(edit);
  private final SideButton btnReverse = new SideButton(reverse);
  private final SideButton btnRestore = new SideButton(restore);
  private final SideButton btnClear = new SideButton(clear);
  private final SideButton btnDelete = new SideButton(delete);
  
  private final JPopupMenu listPopupMenu = new JPopupMenu();
  private final JMenuItem rememberItem = new JMenuItem();
  private final AbstractAction remember;
  private final AbstractAction unremember;
  
  public ExtendedClipboardDialog() {
    super(tr("Extended Clipboard"), "extendedclipboard", tr("Store selection for later reselection."),
        Shortcut.registerShortcut("ExtendedClipBoardDialog.extendedclipboard", tr("Windows: {0}", tr("Extended Clipboard")), KeyEvent.VK_B,
                Shortcut.ALT_SHIFT), 150, true);
    modelTable = new Hashtable<>();
    clipboard = new JList<>();
    add.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_D, 0));
    addNew.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
    
    remember = new AbstractAction(tr("Remember list name"), ImageProvider.get("save")) {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        final List<String> list = Config.getPref().getList(PREF_NAMES);
        final String name = clipboard.getSelectedValue().name;
        
        if(!list.contains(name)) {
          ArrayList<String> result = new ArrayList<>();
          result.addAll(list);
          result.add(name);
          
          if(result.size() > MAX_MODEL_SIZE) {
            result.remove(0);
          }
          
          Config.getPref().putList(PREF_NAMES, result);
        }
      }
    };
    unremember = new AbstractAction(tr("Unremember list name"), ImageProvider.get("purge")) {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        final List<String> list = Config.getPref().getList(PREF_NAMES);
        final String name = clipboard.getSelectedValue().name;
        
        if(list.contains(name)) {
          ArrayList<String> result = new ArrayList<>();
          result.addAll(list);
          result.remove(name);
          
          Config.getPref().putList(PREF_NAMES, result);
        }
      }
    };
    
    createListPopupMenu();
    
    clipboard.addListSelectionListener(e -> {
      if(!e.getValueIsAdjusting()) {
        updateBtnEnabledState();
      }
    });
    clipboard.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    MouseAdapter mouseAdapter = new MouseAdapter() {
      private int indexSelected = -1;
      private int indexMouse = -1;
      
      @Override
      public void mouseReleased(MouseEvent e) {
        if(SwingUtilities.isLeftMouseButton(e)) {
          int curIndex = clipboard.locationToIndex(e.getPoint());
          
          if(indexSelected == curIndex && curIndex >= 0 && clipboard.isSelectedIndex(curIndex)) {
            clipboard.clearSelection();
            indexSelected = -1;
          }
          else {
            indexSelected = clipboard.getSelectedIndex();
          }
        }
        else {
          handlePopupMenu(e);
        }
      }
      
      @Override
      public void mousePressed(MouseEvent e) {
        handlePopupMenu(e);
      }
      
      private void handlePopupMenu(MouseEvent e) {
        int index = clipboard.locationToIndex(e.getPoint());
        
        if(e.isPopupTrigger()) {
          if(index >= 0 && clipboard.getCellBounds(index, index).contains(e.getPoint())) {
            clipboard.setSelectedIndex(index);
          }
          else {
            clipboard.clearSelection();
          }
          
          updateListPopupMenu(index >= 0 && clipboard.getCellBounds(index, index).contains(e.getPoint()));
          listPopupMenu.show(e.getComponent(), e.getPoint().x, e.getPoint().y);
        }
      }
      
      @Override
      public void mouseMoved(MouseEvent e) {
        
        int index = clipboard.locationToIndex(e.getPoint());
        
        if(index >= 0 && !clipboard.getCellBounds(index, index).contains(e.getPoint())) {
          index = -1;
        }
        
        if(index != indexMouse) {
          if(indexMouse >= 0) {
            model.get(indexMouse).highlight(false);
          }
          
          if(index >= 0) {
            model.get(index).highlight(true);
          }
          
          indexMouse = index;
        }
      }
      
      @Override
      public void mouseEntered(MouseEvent e) {
        indexMouse = clipboard.locationToIndex(e.getPoint());
        
        if(indexMouse >= 0) {
          model.get(indexMouse).highlight(true);
        }
      }
      
      @Override
      public void mouseExited(MouseEvent e) {
        if(indexMouse >= 0) {
          model.get(indexMouse).highlight(false);
        }
        
        indexMouse = -1;
      }
      
      @Override
      public void mouseClicked(MouseEvent e) {
        if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
          int index = clipboard.locationToIndex(e.getPoint());
          
          if(index >= 0 && model != null) {
            model.get(index).restore();
          }
          
          clipboard.setSelectedIndex(index);
        }
      }
    };
    clipboard.addMouseListener(mouseAdapter);
    clipboard.addMouseMotionListener(mouseAdapter);
    
    clipboard.getActionMap().put("clipboard.add", add);
    clipboard.getActionMap().put("clipboard.addNew", addNew);
        
    Component c = createLayout(clipboard, true, Arrays.asList(this.btnAdd, this.btnRemove, this.btnClear, this.btnReverse, this.btnRestore, this.btnNewClipboard, this.btnEdit, this.btnDelete));
    clipboard.setSize(c.getSize());
  }
  
  private void createListPopupMenu() {
    rememberItem.setAction(remember);
    
    listPopupMenu.add(rememberItem);
    listPopupMenu.addSeparator();
    listPopupMenu.add(add);
    listPopupMenu.add(addNew);
    listPopupMenu.add(remove);
    listPopupMenu.add(clear);
    listPopupMenu.add(reverse);
    listPopupMenu.add(restore);
    listPopupMenu.addSeparator();
    listPopupMenu.add(clipboardNew);
    listPopupMenu.add(delete);
  }
  
  private void updateListPopupMenu(boolean isOnEntry) {
    rememberItem.setEnabled(isOnEntry);
    
    if(isOnEntry && clipboard.getSelectedIndex() >= 0) {
      if(Config.getPref().getList(PREF_NAMES).contains(clipboard.getSelectedValue().name)) {
        rememberItem.setAction(unremember);
      }
      else {
        rememberItem.setAction(remember);
      }
    }
  }

  private void addNewClipboardEntry() {
    addNewClipboardEntry(null);
    updateBtnEnabledState();
  }
  
  private ClipboardEntry addNewClipboardEntry(final Collection<OsmPrimitive> selection) {
    ClipboardEntry entry = new ClipboardEntry(selection == null ? OsmDataManager.getInstance().getActiveDataSet().getSelected() : selection);
    model.add(0, entry);
      
    if(model.size() > MAX_MODEL_SIZE) {
      model.removeElementAt(model.size()-1);
    }
      
    clipboard.setSelectedIndex(0);
    updateBtnEnabledState();
    
    return entry;
  }
  
  private void updateBtnEnabledState() {
    edit.updateEnabledState();
    reverse.updateEnabledState();
    restore.updateEnabledState();
    delete.updateEnabledState();
    clear.updateEnabledState();
    remove.updateEnabledState();
  }
  
  @Override
  public void showNotify() {
    SelectionEventManager.getInstance().addSelectionListenerForEdt(this);
    MainApplication.getLayerManager().addActiveLayerChangeListener(this);
    clipboardNew.setEnabled(model != null);
  }
  
  @Override
  public synchronized void hideNotify() {
    SelectionEventManager.getInstance().removeSelectionListener(this);
    MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
    clipboardNew.setEnabled(model != null);
  }
  
  public void clear() {
    if(model != null) {
      model.clear();
    }
  }

  @Override
  public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
    clipboard.setModel(EMPTY_MODEL);
    model = null;
    OsmDataLayer layer = e.getSource().getActiveDataLayer();
    
    if(layer != null) {
      clipboardNew.setEnabled(true);
      model = modelTable.get(layer);
      
      if(model == null) {
        model = new DefaultListModel<>();
        modelTable.put(layer, model);
        
        Config.getPref().getList(PREF_NAMES).forEach(entry -> {
          model.addElement(new ClipboardEntry(entry));
        });
      }
      
      clipboard.setModel(model);
    }
  }

  @Override
  public void selectionChanged(SelectionChangeEvent event) {
    add.updateEnabledState();
    addNew.updateEnabledState();
    remove.updateEnabledState();
  }
  
  
  class NewClipboardAction extends JosmAction {
    NewClipboardAction() {
        super(tr("Add new clipboard"), /* ICON() */ "addclipboard", tr("Create new clipboard"),/*Shortcut*/ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      addNewClipboardEntry();
    }
  }
  
  class EditAction extends JosmAction {
    EditAction() {
        super(tr("Rename clipboard"), /* ICON() */ "dialogs/edit", tr("Edit name of clipboard entry"),/*Shortcut*/ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      ClipboardEntry entry = clipboard.getSelectedValue();
      String result = JOptionPane.showInputDialog(MainApplication.getMainFrame(), tr("Name:"), entry.name);
      
      if(result != null && !result.isBlank()) {
        entry.name = result;
        clipboard.repaint();
      }
    }
    

    @Override
    protected final void updateEnabledState() {
      setEnabled(clipboard != null && clipboard.getSelectedIndex() >= 0);
    }
  }
  
  class ClearAction extends JosmAction {
    ClearAction() {
        super(tr("Clear selected clipboard"), /* ICON() */ "purge", tr("Clear selection from selected clipboard"),/*Shortcut*/ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      clipboard.getSelectedValue().selection.clear();
      updateBtnEnabledState();
    }
    

    @Override
    protected final void updateEnabledState() {
      setEnabled(clipboard != null && clipboard.getSelectedIndex() >= 0 && !clipboard.getSelectedValue().isEmpty());
    }
  }

  class DeleteAction extends JosmAction {
    DeleteAction() {
        super(tr("Delete selected clipboard"), /* ICON() */ "dialogs/delete", tr("Delete selected clipboard from list"),/*Shortcut*/ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      unremember.actionPerformed(null);
      model.removeElement(clipboard.getSelectedValue());
      
      if(model.size() > 0) {
        clipboard.setSelectedIndex(0);
        clipboard.requestFocusInWindow();
      }
    }
    

    @Override
    protected final void updateEnabledState() {
      setEnabled(clipboard != null && clipboard.getSelectedIndex() >= 0);
    }
  }
  
  class ReverseAction extends JosmAction {
    ReverseAction() {
        super(tr("Reverse selection order"), /* ICON() */ "dialogs/reverse", tr("Reverse order of selection of selected clipboard entry"),/*Shortcut*/ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      LinkedHashSet<OsmPrimitive> reverse = new LinkedHashSet<>();
      
      ClipboardEntry entry = clipboard.getSelectedValue();
      OsmPrimitive[] arr = entry.selection.toArray(new OsmPrimitive[0]); 
      
      for(int i = arr.length-1; i >= 0; i--) {
        reverse.add(arr[i]);
      }
      
      if((e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK) {
        final String name = entry.name;
        
        entry = addNewClipboardEntry(reverse);
        entry.name = name;
      }
      else {
        entry.selection = reverse;
      }
      
      if(entry.name.endsWith("\u2B07")) {
        entry.name = entry.name.substring(0,entry.name.length()-2);
      }
      else {
        entry.name = entry.name + " \u2B07";
      }
      
      updateBtnEnabledState();
      clipboard.repaint();
    }
    

    @Override
    protected final void updateEnabledState() {
      setEnabled(clipboard != null && clipboard.getSelectedIndex() >= 0 && clipboard.getSelectedValue().selection.size() > 1);
    }
  }
  
  class AddAction extends JosmAction {
    AtomicBoolean isPerforming = new AtomicBoolean(false);
    AddAction() {
        super(tr("Add selection to selected clipboard"), /* ICON() */ "dialogs/add", tr("Add current selection to extended clipboard"),
                Shortcut.registerShortcut("extendedclipboard.add", tr("Add Selection to Extended Clipboard"), KeyEvent.VK_D,
                        Shortcut.DIRECT), false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!/*successful*/isPerforming.compareAndSet(false, true)) {
            return;
        }
        try {
          ClipboardEntry entry = clipboard.getSelectedValue();
          
          if((entry == null || ((e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK)) && model != null) {
            addNewClipboardEntry();
          }
          else {
            entry.selection.addAll(OsmDataManager.getInstance().getActiveDataSet().getSelected());
            updateBtnEnabledState();
          }
        } finally {
            isPerforming.set(false);
        }
    }

    @Override
    protected final void updateEnabledState() {
        DataSet ds = OsmDataManager.getInstance().getActiveDataSet();
        setEnabled(ds != null && !ds.isLocked() &&
                !Utils.isEmpty(OsmDataManager.getInstance().getInProgressSelection()) && !ds.isEmpty() && model != null);
    }
  }
  
  class AddNewAction extends JosmAction {
    AtomicBoolean isPerforming = new AtomicBoolean(false);
    AddNewAction() {
        super(tr("Add selection as new clipboard"), /* ICON() */ "addnew", tr("Add current selection to extended clipboard as a new entry"),
                Shortcut.registerShortcut("extendedclipboard.addNew", tr("Add Selection to Extended Clipboard as New Entry"), KeyEvent.VK_D,
                        Shortcut.CTRL_SHIFT), false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!/*successful*/isPerforming.compareAndSet(false, true)) {
            return;
        }
        try {
          addNewClipboardEntry();
        } finally {
            isPerforming.set(false);
        }
    }

    @Override
    protected final void updateEnabledState() {
        DataSet ds = OsmDataManager.getInstance().getActiveDataSet();
        setEnabled(ds != null && !ds.isLocked() &&
                !Utils.isEmpty(OsmDataManager.getInstance().getInProgressSelection()) && !ds.isEmpty() && model != null);
    }
  }
  
  class RemoveAction extends JosmAction {
    AtomicBoolean isPerforming = new AtomicBoolean(false);
    RemoveAction() {
        super(tr("Remove selection from selected clipboard"), /* ICON() */ "remove", tr("Remove current selection from the selected extended clipboard"),/*Shortcut*/ null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!/*successful*/isPerforming.compareAndSet(false, true)) {
            return;
        }
        try {
          clipboard.getSelectedValue().remove(OsmDataManager.getInstance().getActiveDataSet().getSelected());
          updateBtnEnabledState();
        } finally {
            isPerforming.set(false);
        }
    }

    @Override
    protected final void updateEnabledState() {
        DataSet ds = OsmDataManager.getInstance().getActiveDataSet();
        setEnabled(ds != null && !ds.isLocked() &&
                !Utils.isEmpty(OsmDataManager.getInstance().getInProgressSelection()) && !ds.isEmpty() && model != null
                && clipboard.getSelectedIndex() >= 0 && clipboard.getSelectedValue().contains(OsmDataManager.getInstance().getActiveDataSet().getSelected())
            );
    }
  }
  
  class RestoreAction extends JosmAction {
    RestoreAction() {
        super(tr("Restore selection"), /* ICON() */ "apply", tr("Restore the selection of the selected clipboard"),/*Shortcut*/ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      clipboard.getSelectedValue().restore();
    }
    

    @Override
    protected final void updateEnabledState() {
      setEnabled(clipboard != null && clipboard.getSelectedIndex() >= 0 && !clipboard.getSelectedValue().isEmpty());
    }
  }
  
  private final static class ClipboardEntry {
    LinkedHashSet<OsmPrimitive> selection;
    String name;
    
    private ClipboardEntry(String name) {
      this(name, null);
    }
    
    private ClipboardEntry(Collection<OsmPrimitive> selection) {
      this(FORMAT_DATE.format(new Date()), selection);
    }
    
    private ClipboardEntry(String name, Collection<OsmPrimitive> selection) {
      this.selection = new LinkedHashSet<>();
      
      if(selection != null) {
        this.selection.addAll(selection);
      }
      
      this.name = name; 
    }
    
    @Override
    public String toString() {
      return name;
    }
    
    private void restore() {
      OsmDataManager.getInstance().getActiveDataSet().setSelected(selection);
    }
    
    private void highlight(boolean value) {
      selection.forEach(e -> {
        if(e instanceof Relation) {
          for(OsmPrimitive osm : ((Relation) e).getMemberPrimitivesList()) {
            osm.setHighlighted(value);
          }
        }
        else {
          e.setHighlighted(value);
        }
      });
    }
    
    private boolean isEmpty() {
      return selection.isEmpty();
    }
    
    private boolean contains(Collection<OsmPrimitive> selection) {
      for(OsmPrimitive sel : selection) {
        if(this.selection.contains(sel)) {
          return true;
        }
      }
      
      return false;
    }
    
    private void remove(Collection<OsmPrimitive> selection) {
      this.selection.removeAll(selection);
    }
  }
}
