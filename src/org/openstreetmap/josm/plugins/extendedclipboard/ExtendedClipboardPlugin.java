// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.extendedclipboard;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

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
  }

  @Override
  public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
    if (oldFrame == null && newFrame != null) {
      dialog = new ExtendedClipboardDialog();
      nodeTemplateDialog = new NodeTemplateListDialog();
      newFrame.addToggleDialog(nodeTemplateDialog);
      newFrame.addToggleDialog(dialog);
    } else if (oldFrame != null && newFrame == null) {
      dialog.clear();
      dialog = null;
      nodeTemplateDialog = null;
    }
  }

  public static final ExtendedClipboardPlugin getInstance() {
    return instance;
  }
}
