// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.extendedclipboard;

import javax.swing.Action;
import javax.swing.JMenuItem;

import org.openstreetmap.josm.actions.JosmAction;

public final class MenuUtils {
  public static JMenuItem createJMenuItemFrom(JosmAction a) {
    final JMenuItem item = new JMenuItem(a);
    item.setText((String)a.getValue(Action.SHORT_DESCRIPTION));
    
    return item;
  }
}
