// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.extendedclipboard;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
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
import java.util.Objects;
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
import org.openstreetmap.josm.actions.relation.SelectInRelationListAction;
import org.openstreetmap.josm.actions.relation.SelectMembersAction;
import org.openstreetmap.josm.actions.relation.SelectRelationAction;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageResource;
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
  private final ReverseActionAdd reverseAdd = new ReverseActionAdd();
  private final RestoreAction restore = new RestoreAction();
  private final ClearAction clear = new ClearAction();
  private final DeleteAction delete = new DeleteAction();
  private final SelectNodesAction selectNodes = new SelectNodesAction();
  private final SelectWaysAction selectWays = new SelectWaysAction();
  
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
  
  private final JPopupMenu selectionPopupMenu = new JPopupMenu();
  
  private final SelectInRelationListAction selectInRelationList = new SelectInRelationListAction();
  private final SelectRelationAction selectRelations = new SelectRelationAction(false);
  private final SelectMembersAction selectRelationMembers = new SelectMembersAction(false);
  
  private boolean iconAddToNewList = true;
  
  public ExtendedClipboardDialog() {
    super(tr("Extended Clipboard"), "extendedclipboard", tr("Store selection for later reselection."),
        Shortcut.registerShortcut("ExtendedClipBoardDialog.extendedclipboard", tr("Windows: {0}", tr("Extended Clipboard")), KeyEvent.VK_B,
                Shortcut.ALT_SHIFT), 150, true);
    modelTable = new Hashtable<>();
    clipboard = new JList<>();    
    remember = new AbstractAction(tr("Remember clipboard name"), ImageProvider.get("save")) {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        final List<String> list = Config.getPref().getList(PREF_NAMES);
        final String name = clipboard.getSelectedValue().getNameOnly();
        
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
    unremember = new AbstractAction(tr("Unremember clipboard name"), ImageProvider.get("purge")) {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        final List<String> list = Config.getPref().getList(PREF_NAMES);
        final String name = clipboard.getSelectedValue().getNameOnly();
        
        if(list.contains(name)) {
          ArrayList<String> result = new ArrayList<>();
          result.addAll(list);
          result.remove(name);
          
          Config.getPref().putList(PREF_NAMES, result);
        }
      }
    };
    
    createListPopupMenu();
    createSelectionPopupMenu();
    
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
          
          updatePopupMenus(index >= 0 && clipboard.getCellBounds(index, index).contains(e.getPoint()));
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
        
        updateBtnEnabledState();
      }
    };
    clipboard.addMouseListener(mouseAdapter);
    clipboard.addMouseMotionListener(mouseAdapter);
    
    final AbstractAction iconListener = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateBtnAddIconAndTooltip(OsmDataManager.getInstance().getActiveDataSet(), (e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK);
        updateBtnReverseIconAndTooltip(OsmDataManager.getInstance().getActiveDataSet(), (e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK);
      }
    };
    
    clipboard.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_DOWN_MASK), "CTRL_DOWN");
    clipboard.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, 0, true), "CTRL_UP");
    clipboard.getActionMap().put("clipboard.add", add);
    clipboard.getActionMap().put("clipboard.addNew", addNew);
    clipboard.getActionMap().put("CTRL_DOWN", iconListener);
    clipboard.getActionMap().put("CTRL_UP", iconListener);
    
    btnAdd.setIcon(((ImageResource)addNew.getValue("ImageResource")).getImageIconBounded(ImageProvider.ImageSizes.SIDEBUTTON.getImageDimension()));
    btnAdd.setToolTipText((String)addNew.getValue(Action.SHORT_DESCRIPTION));
    
    Component c = createLayout(clipboard, true, Arrays.asList(this.btnAdd, this.btnRemove, this.btnClear, this.btnReverse, this.btnRestore, this.btnNewClipboard, this.btnEdit, this.btnDelete));
    clipboard.setSize(c.getSize());
  }
  
  private void createSelectionPopupMenu() {
    selectionPopupMenu.add(MenuUtils.createJMenuItemFrom(selectNodes));
    selectionPopupMenu.add(MenuUtils.createJMenuItemFrom(selectWays));
    selectionPopupMenu.addSeparator();
    selectionPopupMenu.add(selectInRelationList);
    selectionPopupMenu.add(selectRelations);
    selectionPopupMenu.add(selectRelationMembers);
    
    btnRestore.createArrow(e -> {
      Rectangle r = btnRestore.getBounds();
      updatePopupMenus(clipboard.getSelectedIndex() >= 0);
      selectionPopupMenu.show(btnRestore, r.x, r.height);
    }, true);
  }
  
  private void createListPopupMenu() {
    rememberItem.setAction(remember);
    
    listPopupMenu.add(rememberItem);
    listPopupMenu.addSeparator();
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(add));
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(addNew));
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(remove));
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(clear));
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(reverse));
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(reverseAdd));
    listPopupMenu.addSeparator();
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(restore));
    listPopupMenu.addSeparator();
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(selectNodes));
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(selectWays));
    listPopupMenu.addSeparator();
    listPopupMenu.add(selectInRelationList);
    listPopupMenu.add(selectRelations);
    listPopupMenu.add(selectRelationMembers);
    listPopupMenu.addSeparator();
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(clipboardNew));
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(edit));
    listPopupMenu.add(MenuUtils.createJMenuItemFrom(delete));
  }
  
  private void updatePopupMenus(boolean isOnEntry) {
    rememberItem.setEnabled(isOnEntry);
    
    if(isOnEntry && clipboard.getSelectedIndex() >= 0) {
      ClipboardEntry entry = clipboard.getSelectedValue();
      
      if(Config.getPref().getList(PREF_NAMES).contains(entry.getNameOnly())) {
        rememberItem.setAction(unremember);
      }
      else {
        rememberItem.setAction(remember);
      }
            
      selectInRelationList.setEnabled(entry.containsRelations());
      selectRelations.setEnabled(entry.containsRelations());
      selectRelationMembers.setEnabled(entry.containsRelations());
      
      if(entry.containsRelations()) {
        selectInRelationList.setPrimitives(entry.getRelations());
        selectRelations.setPrimitives(entry.getRelations());
        selectRelationMembers.setPrimitives(entry.getRelations());
      }
    }
    else {
      selectInRelationList.setEnabled(false);
      selectRelations.setEnabled(false);
      selectRelationMembers.setEnabled(false);
    }
  }

  private void addNewClipboardEntry() {
    addNewClipboardEntry(null, null);
    updateBtnEnabledState();
  }
  
  private ClipboardEntry addNewClipboardEntry(final Collection<OsmPrimitive> selection, String name) {
    ClipboardEntry entry = new ClipboardEntry(name, selection == null ? OsmDataManager.getInstance().getActiveDataSet().getSelected() : selection);
    model.add(0, entry);
      
    if(model.size() > MAX_MODEL_SIZE) {
      model.removeElementAt(model.size()-1);
    }
      
    clipboard.setSelectedIndex(0);
    updateBtnEnabledState();
    
    return entry;
  }
  
  private void updateBtnEnabledState() {
    add.updateEnabledState();
    edit.updateEnabledState();
    reverse.updateEnabledState();
    reverseAdd.updateEnabledState();
    restore.updateEnabledState();
    delete.updateEnabledState();
    clear.updateEnabledState();
    remove.updateEnabledState();
    selectNodes.updateEnabledState();
    selectWays.updateEnabledState();
    repaintRow(clipboard.getSelectedIndex());
  }

  private void repaintRow(int index) {
    if(index >= 0) {
      clipboard.repaint(clipboard.getCellBounds(index, index));
    }
  }
  
  private void updateBtnAddIconAndTooltip(DataSet ds, boolean ctrl_down) {
    if(clipboard != null && model != null && ds != null) {
      if((iconAddToNewList && clipboard.getSelectedIndex() >= 0 && !clipboard.getSelectedValue().containsAll(ds.getSelected()))) {
        btnAdd.setIcon(((ImageResource)add.getValue("ImageResource")).getImageIconBounded(ImageProvider.ImageSizes.SIDEBUTTON.getImageDimension()));
        btnAdd.setToolTipText((String)add.getValue(Action.SHORT_DESCRIPTION));
        iconAddToNewList = false;
      }
      else if(ctrl_down || (!iconAddToNewList && (clipboard.getSelectedIndex() < 0 || clipboard.getSelectedValue().containsAll(ds.getSelected())))) {
        btnAdd.setIcon(((ImageResource)addNew.getValue("ImageResource")).getImageIconBounded(ImageProvider.ImageSizes.SIDEBUTTON.getImageDimension()));
        btnAdd.setToolTipText((String)addNew.getValue(Action.SHORT_DESCRIPTION));
        iconAddToNewList = true;
      }
    }
  }
  
  private void updateBtnReverseIconAndTooltip(DataSet ds, boolean ctrl_down) {
    if(clipboard != null && model != null && clipboard.getSelectedIndex() >= 0 && clipboard.getSelectedValue().size() > 1 && ctrl_down) {
      btnReverse.setIcon(((ImageResource)reverseAdd.getValue("ImageResource")).getImageIconBounded(ImageProvider.ImageSizes.SIDEBUTTON.getImageDimension()));
      btnReverse.setToolTipText((String)reverseAdd.getValue(Action.SHORT_DESCRIPTION));
    }
    else {
      btnReverse.setIcon(((ImageResource)reverse.getValue("ImageResource")).getImageIconBounded(ImageProvider.ImageSizes.SIDEBUTTON.getImageDimension()));
      btnReverse.setToolTipText((String)reverse.getValue(Action.SHORT_DESCRIPTION));
    }
  }
  
  @Override
  public void showNotify() {
    SelectionEventManager.getInstance().addSelectionListenerForEdt(this);
    MainApplication.getLayerManager().addActiveLayerChangeListener(this);
    clipboardNew.setEnabled(model != null);
    updateBtnEnabledState();
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
      
      if(!model.isEmpty()) {
        clipboard.setSelectedIndex(0);
      }
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
        super(null, /* ICON() */ "addclipboard", tr("Create new clipboard"),/*Shortcut*/ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      addNewClipboardEntry();
    }
  }
  
  class EditAction extends JosmAction {
    EditAction() {
        super(null, /* ICON() */ "dialogs/edit", tr("Edit name of selected clipboard "),/*Shortcut*/ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      ClipboardEntry entry = clipboard.getSelectedValue();
      String result = JOptionPane.showInputDialog(MainApplication.getMainFrame(), tr("Name:"), entry.getNameOnly());
      
      if(result != null && !result.isBlank()) {
        entry.setName(result);
        repaintRow(clipboard.getSelectedIndex());
      }
    }
    

    @Override
    protected final void updateEnabledState() {
      setEnabled(clipboard != null && clipboard.getSelectedIndex() >= 0);
    }
  }
  
  class ClearAction extends JosmAction {
    ClearAction() {
        super(null, /* ICON() */ "purge", tr("Clear selected clipboard"),/*Shortcut*/ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      clipboard.getSelectedValue().clear();
      updateBtnEnabledState();
    }
    

    @Override
    protected final void updateEnabledState() {
      setEnabled(clipboard != null && clipboard.getSelectedIndex() >= 0 && !clipboard.getSelectedValue().isEmpty());
    }
  }

  class DeleteAction extends JosmAction {
    DeleteAction() {
        super(null, /* ICON() */ "dialogs/delete", tr("Delete selected clipboard"),/*Shortcut*/ null, false);
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
        super(null, /* ICON() */ "dialogs/reverse", tr("Reverse order of objects of selected clipboard"),/*Shortcut*/ null, false);
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
        entry = addNewClipboardEntry(reverse, entry.getNameOnly());
      }
      else {
        entry.selection = reverse;
      }
      
      entry.updateReverseState();
      
      updateBtnEnabledState();
      clipboard.repaint();
    }
    

    @Override
    protected final void updateEnabledState() {
      setEnabled(clipboard != null && clipboard.getSelectedIndex() >= 0 && clipboard.getSelectedValue().size() > 1);
    }
  }
  
  class ReverseActionAdd extends JosmAction {
    ReverseActionAdd() {
      super(null, /* ICON() */ "reverseadd", tr("Reverse order of objects of selected clipboard and add them to new clipboard"),/*Shortcut*/ null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      reverse.actionPerformed(new ActionEvent(e.getSource(), e.getID(), e.getActionCommand(), ActionEvent.CTRL_MASK));
    }
    
    @Override
    protected final void updateEnabledState() {
      setEnabled(clipboard != null && clipboard.getSelectedIndex() >= 0 && clipboard.getSelectedValue().size() > 1);
    }
  }
  
  class AddAction extends JosmAction {
    AtomicBoolean isPerforming = new AtomicBoolean(false);
    AddAction() {
        super(null, /* ICON() */ "dialogs/add", tr("Add current selection to selected clipboard"),
                Shortcut.registerShortcut("extendedclipboard.add", tr("Add Selection to Extended Clipboard"), KeyEvent.VK_D,
                        Shortcut.DIRECT), false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!/*successful*/isPerforming.compareAndSet(false, true)) {
            return;
        }
        try {
          final ClipboardEntry entry = clipboard.getSelectedValue();
          final Collection<OsmPrimitive> selection = OsmDataManager.getInstance().getActiveDataSet().getSelected();
          
          if((entry == null || ((e.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK)
              || clipboard.getSelectedIndex() < 0 || clipboard.getSelectedValue().containsAll(selection)
              ) && model != null) {
            addNewClipboardEntry();
          }
          else {
            entry.addAll(selection);
            updateBtnEnabledState();
          }
        } finally {
            isPerforming.set(false);
        }
    }

    @Override
    protected final void updateEnabledState() {
        DataSet ds = OsmDataManager.getInstance().getActiveDataSet();
        
        updateBtnAddIconAndTooltip(ds, false);
        
        setEnabled(ds != null && !ds.isLocked() &&
                !Utils.isEmpty(OsmDataManager.getInstance().getInProgressSelection()) && !ds.isEmpty() && model != null);
    }
  }
  
  class AddNewAction extends JosmAction {
    AtomicBoolean isPerforming = new AtomicBoolean(false);
    AddNewAction() {
        super(null, /* ICON() */ "addnew", tr("Add current selection to new clipboard"),
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
        super(null, /* ICON() */ "remove", tr("Remove current selection from selected clipboard"),/*Shortcut*/ null, false);
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
        super(null, /* ICON() */ "dialogs/select", tr("Restore the selection of the selected clipboard"),/*Shortcut*/ null, false);
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
  
  class SelectNodesAction extends JosmAction {
    SelectNodesAction() {
        super(null, /* ICON() */ "data/node", tr("Select nodes"),/*Shortcut*/ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      clipboard.getSelectedValue().restore(ClipboardEntry.NODES);
    }
    

    @Override
    protected final void updateEnabledState() {
      setEnabled(clipboard != null && clipboard.getSelectedIndex() >= 0 && clipboard.getSelectedValue().containsNodes());
    }
  }
  
  class SelectWaysAction extends JosmAction {
    SelectWaysAction() {
        super(null, /* ICON() */ "data/way", tr("Select ways"),/*Shortcut*/ null, false);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
      clipboard.getSelectedValue().restore(ClipboardEntry.WAYS);
    }

    @Override
    protected final void updateEnabledState() {
      setEnabled(clipboard != null && clipboard.getSelectedIndex() >= 0 && clipboard.getSelectedValue().containsWays());
    }
  }
  
  private final static class ClipboardEntry {
    private static final String NODES = "nodes";
    private static final String WAYS = "ways";
    private static final String RELATIONS = "relations";
    private static final String ALL = "relations";
    
    private static final String FORMAT_EXT = " ({1},{2},{3}) ({0})";
    private static final String FORMAT_EXT_PATTERN =  FORMAT_EXT.replaceAll("\\{\\d{1}\\}", "\\\\d+").replace("(", "\\\\(").replace(")", "\\\\)");
    private static final String REVERSE_INFO = " \u2B07";
    
    private LinkedHashSet<OsmPrimitive> selection;
    
    private LinkedHashSet<OsmPrimitive> nodes;
    private LinkedHashSet<OsmPrimitive> ways;
    private LinkedHashSet<OsmPrimitive> relations;
    
    private String name;
    private String nameTemplate;
    private String baseName;
    
    private ClipboardEntry(String name) {
      this(name, null);
    }
    
    private ClipboardEntry(Collection<OsmPrimitive> selection) {
      this(FORMAT_DATE.format(new Date()), selection);
    }
    
    private ClipboardEntry(String name, Collection<OsmPrimitive> selection) {
      this.selection = new LinkedHashSet<>();
      this.nodes = new LinkedHashSet<>();
      this.ways = new LinkedHashSet<>();
      this.relations = new LinkedHashSet<>();
      
      if(name == null) {
        name = FORMAT_DATE.format(new Date());
      }
      
      setName(name);
      
      if(selection != null) {
        addAll(selection);
      }
    }
    
    public String getNameOnly() {
      return baseName;
    }
    
    @Override
    public String toString() {
      return name;
    }
    
    private void restore() {
      restore(ALL);
    }
    
    private void restore(String type) {
      if(!isEmpty()) {
        OsmDataManager.getInstance().getActiveDataSet().clearSelection();
        
        if(Objects.equals(ALL, type)) {
          OsmDataManager.getInstance().getActiveDataSet().setSelected(selection);
        }
        else if(Objects.equals(RELATIONS, type)) {
          OsmDataManager.getInstance().getActiveDataSet().setSelected(relations);
        }
        else if(Objects.equals(WAYS, type)) {
          OsmDataManager.getInstance().getActiveDataSet().setSelected(ways);
        }
        else if(Objects.equals(NODES, type)) {
          OsmDataManager.getInstance().getActiveDataSet().setSelected(nodes);
        }
      }
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
    
    private boolean containsAll(Collection<OsmPrimitive> selection) {
      return this.selection != null && this.selection.containsAll(selection);
    }
    
    private boolean contains(Collection<OsmPrimitive> selection) {
      for(OsmPrimitive sel : selection) {
        if(this.selection.contains(sel)) {
          return true;
        }
      }
      
      return false;
    }
    
    private void updateReverseState() {
      if(nameTemplate.contains(REVERSE_INFO)) {
        nameTemplate = baseName+FORMAT_EXT;
      }
      else {
        nameTemplate = baseName+FORMAT_EXT+REVERSE_INFO;
      }
      
      refreshName();
    }
    
    private void setName(String name) {
      if(name.matches(".*"+FORMAT_EXT_PATTERN+".*")) {
        baseName = name.replaceAll(FORMAT_EXT_PATTERN, "");
      }
      else {
        baseName = name;
      }
      
      nameTemplate = baseName+FORMAT_EXT;
      
      if(nameTemplate.contains(REVERSE_INFO) && !nameTemplate.endsWith(REVERSE_INFO)) {
        nameTemplate = nameTemplate.replace(REVERSE_INFO, "") + REVERSE_INFO;
      }
      
      refreshName();
    }
    
    private void addAll(Collection<OsmPrimitive> selection) {
      for(OsmPrimitive s : selection) {
        if(!this.selection.contains(s)) {
          this.selection.add(s);
          
          if(s instanceof Node) {
            this.nodes.add(s);
          }
          else if(s instanceof Way) {
            this.ways.add(s);
          }
          else if(s instanceof Relation) {
            this.relations.add(s);
          }
        }
      }
      refreshName();
    }
    
    private void remove(Collection<OsmPrimitive> selection) {
      this.selection.removeAll(selection);
      this.nodes.removeAll(selection);
      this.ways.removeAll(selection);
      this.relations.removeAll(selection);
      
      refreshName();
    }
    
    private boolean containsNodes() {
      return !nodes.isEmpty();
    }
        
    private boolean containsWays() {
      return !ways.isEmpty();
    }
    
    private boolean containsRelations() {
      return !relations.isEmpty();
    }
    
    public LinkedHashSet<OsmPrimitive> getRelations() {
      return relations;
    }
    
    private void refreshName() {
      name = nameTemplate.replace("{0}", String.valueOf(size())).replace("{1}", String.valueOf(nodes.size())).replace("{2}", String.valueOf(ways.size())).replace("{3}", String.valueOf(relations.size()));
    }
    
    private void clear() {
      this.selection.clear();
      this.nodes.clear();
      this.ways.clear();
      this.relations.clear();
      
      refreshName();
    }
    
    private int size() {
      if(selection != null) {
        return selection.size();
      }
      else {
        return 0;
      }
    }
  }
}
