// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.extendedclipboard;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.extendedclipboard.nodetemplatelist.NodeTemplateList;
import org.openstreetmap.josm.plugins.extendedclipboard.nodetemplatelist.NodeTemplateListDialog;

/**
 * Collection of utilities
 */
public class ExtendedClipboardPlugin extends Plugin {
  private static ExtendedClipboardPlugin instance;

  private ExtendedClipboardDialog dialog;
  private NodeTemplateListDialog nodeTemplateDialog;

  public ExtendedClipboardPlugin(PluginInformation info) {
    super(info);
    instance = this;
    NodeTemplateList.initialize();
  }

  @Override
  public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
    if (oldFrame != null) {
      oldFrame.removeToggleDialog(dialog);
      oldFrame.removeToggleDialog(nodeTemplateDialog);
      NodeTemplateList.get().setDialog(null);
      dialog.clear();
      dialog = null;
      nodeTemplateDialog = null;
    }
    
    if (newFrame != null) {
      nodeTemplateDialog = new NodeTemplateListDialog();
      NodeTemplateList.get().setDialog(nodeTemplateDialog);
      dialog = new ExtendedClipboardDialog();
      newFrame.addToggleDialog(nodeTemplateDialog);
      newFrame.addToggleDialog(dialog);
    }
  }

  public static final ExtendedClipboardPlugin getInstance() {
    return instance;
  }
}
