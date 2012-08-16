/*
 * Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 * 
 * Modified by: Andras Kovi 2012
 */

package sun.tools.jconsole;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.OverlayLayout;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import sun.tools.jconsole.ProxyClient.SnapshotMBeanServerConnection;
import sun.tools.jconsole.inspector.Utils;
import sun.tools.jconsole.inspector.XDataViewer;
import sun.tools.jconsole.inspector.XMBean;
import sun.tools.jconsole.inspector.XNodeInfo;
import sun.tools.jconsole.inspector.XSheet;
import sun.tools.jconsole.inspector.XTree;
import sun.tools.jconsole.inspector.XTree.ComparableDefaultMutableTreeNode;
import sun.tools.jconsole.inspector.XTree.Dn;
import sun.tools.jconsole.inspector.XTreeRenderer;
import andrask.sun.tools.jconsole.Settings;

import com.sun.tools.jconsole.JConsoleContext;

@SuppressWarnings("serial")
public class MBeansTab extends Tab implements
        NotificationListener, PropertyChangeListener,
        TreeSelectionListener, TreeWillExpandListener, DocumentListener {

    private XTree tree;
    private XSheet sheet;
    private XDataViewer viewer;


	private XTree filteredTree;
	private PromptingTextField filterTF;
	private JLabel operationLabel;    
    
    public static String getTabName() {
        return Resources.getText("MBeans");
    }

    public MBeansTab(final VMPanel vmPanel) {
        super(vmPanel, getTabName());
        addPropertyChangeListener(this);
        setupTab();
        
        testerThread.start();
    }

    public XDataViewer getDataViewer() {
        return viewer;
    }

	public XTree getTree() {
		if (isFiltering) {
			return filteredTree;
		} else {
			return tree;
		}
	}

    public XSheet getSheet() {
        return sheet;
    }

    @Override
    public void dispose() {
        super.dispose();
        sheet.dispose();
    }

    public int getUpdateInterval() {
        return vmPanel.getUpdateInterval();
    }

    private void buildMBeanServerView() {
        new SwingWorker<Set<ObjectName>, Void>() {
            @Override
            public Set<ObjectName> doInBackground() {
                // Register listener for MBean registration/unregistration
                //
                try {
                    getMBeanServerConnection().addNotificationListener(
                            MBeanServerDelegate.DELEGATE_NAME,
                            MBeansTab.this,
                            null,
                            null);
                } catch (InstanceNotFoundException e) {
                    // Should never happen because the MBeanServerDelegate
                    // is always present in any standard MBeanServer
                    //
                    if (JConsole.isDebug()) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    if (JConsole.isDebug()) {
                        e.printStackTrace();
                    }
                    vmPanel.getProxyClient().markAsDead();
                    return null;
                }
                // Retrieve MBeans from MBeanServer
                //
                Set<ObjectName> mbeans = null;
                try {
                    mbeans = getMBeanServerConnection().queryNames(null, null);
                } catch (IOException e) {
                    if (JConsole.isDebug()) {
                        e.printStackTrace();
                    }
                    vmPanel.getProxyClient().markAsDead();
                    return null;
                }
                return mbeans;
            }
            @Override
            protected void done() {
                try {
                    // Wait for mbsc.queryNames() result
                    Set<ObjectName> mbeans = get();
                    // Do not display anything until the new tree has been built
                    //
                    tree.setVisible(false);
                    // Cleanup current tree
                    //
                    tree.removeAll();
                    // Add MBeans to tree
                    //
                    tree.addMBeansToView(mbeans);
                    // Display the new tree
                    //
                    tree.setVisible(true);
                } catch (Exception e) {
                    Throwable t = Utils.getActualException(e);
                    if (JConsole.isDebug()) {
                        System.err.println("Problem at MBean tree construction");
                        t.printStackTrace();
                    }
                }
            }
        }.execute();
    }

    public MBeanServerConnection getMBeanServerConnection() {
        return vmPanel.getProxyClient().getMBeanServerConnection();
    }

    public SnapshotMBeanServerConnection getSnapshotMBeanServerConnection() {
        return vmPanel.getProxyClient().getSnapshotMBeanServerConnection();
    }

    @Override
    public void update() {
        // Ping the connection to see if it is still alive. At
        // some point the ProxyClient class should centralize
        // the connection aliveness monitoring and no longer
        // rely on the custom tabs to ping the connections.
        //
        try {
            getMBeanServerConnection().getDefaultDomain();
        } catch (IOException ex) {
            vmPanel.getProxyClient().markAsDead();
        }
    }

    private void setupTab() {
		// set up the split pane with the MBean tree and MBean sheet panels
		setLayout(new BorderLayout());
		JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		mainSplit.setDividerLocation(Settings.getInt(Settings.KEY_MBEANS_VIEW_WIDTH, 250));
		mainSplit.setBorder(BorderFactory.createEmptyBorder());

		mainSplit.addPropertyChangeListener(new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent changeEvent) {
				JSplitPane sourceSplitPane = (JSplitPane) changeEvent.getSource();
				String propertyName = changeEvent.getPropertyName();
				if (propertyName.equals(JSplitPane.LAST_DIVIDER_LOCATION_PROPERTY)) {
					int current = sourceSplitPane.getDividerLocation();
					Settings.setInt(Settings.KEY_MBEANS_VIEW_WIDTH, current);
				}
			}
		});

		// set up the MBean tree panel (left pane)
		tree = new XTree(this);
		tree.setCellRenderer(new XTreeRenderer());
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.addTreeSelectionListener(this);
		tree.addTreeWillExpandListener(this);
		tree.addMouseListener(ml);
		theScrollPane = new JScrollPane(tree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		filteredTree = new XTree(this);
		filteredTree.setCellRenderer(new XTreeRenderer());
		filteredTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		filteredTree.addTreeSelectionListener(this);
		filteredTree.addTreeWillExpandListener(this);
		filteredTree.addMouseListener(ml);
		theFilteredScrollPane = new JScrollPane(filteredTree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		treePanel = new JPanel();
		treePanel.setLayout(new OverlayLayout(treePanel));
		treePanel.add(theScrollPane);

		mainSplit.add(treePanel, JSplitPane.LEFT, 0);

		// set up the MBean sheet panel (right pane)
		viewer = new XDataViewer(this);
		sheet = new XSheet(this);
		mainSplit.add(sheet, JSplitPane.RIGHT, 0);

		add(mainSplit);

		JPanel tabToolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		tabToolPanel.setOpaque(false);

		filterTF = new PromptingTextField("Filter", 20);
		filterTF.getDocument().addDocumentListener(this);
		tabToolPanel.add(filterTF);
		
		operationLabel = new JLabel();
		tabToolPanel.add(operationLabel);

		add(tabToolPanel, BorderLayout.SOUTH);
    }

    /* notification listener:  handleNotification */
    public void handleNotification(
            final Notification notification, Object handback) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                if (notification instanceof MBeanServerNotification) {
                    ObjectName mbean =
                            ((MBeanServerNotification) notification).getMBeanName();
                    if (notification.getType().equals(
                            MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
                        tree.addMBeanToView(mbean);
                    } else if (notification.getType().equals(
                            MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
                        tree.removeMBeanFromView(mbean);
                    }
                }
            }
        });
    }

    /* property change listener:  propertyChange */
    public void propertyChange(PropertyChangeEvent evt) {
        if (JConsoleContext.CONNECTION_STATE_PROPERTY.equals(evt.getPropertyName())) {
            boolean connected = (Boolean) evt.getNewValue();
            if (connected) {
                buildMBeanServerView();
            } else {
                sheet.dispose();
            }
        }
    }

    /* tree selection listener: valueChanged */
    public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node =
                (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        sheet.displayNode(node);
    }
    /* tree mouse listener: mousePressed */
    private MouseListener ml = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getClickCount() == 1) {
                int selRow = tree.getRowForLocation(e.getX(), e.getY());
                if (selRow != -1) {
                    TreePath selPath =
                            tree.getPathForLocation(e.getX(), e.getY());
                    DefaultMutableTreeNode node =
                            (DefaultMutableTreeNode) selPath.getLastPathComponent();
                    if (sheet.isMBeanNode(node)) {
                        tree.expandPath(selPath);
                    }
                }
            }
        }
    };

    /* tree will expand listener: treeWillExpand */
    public void treeWillExpand(TreeExpansionEvent e)
            throws ExpandVetoException {
        TreePath path = e.getPath();
        if (!getTree().hasBeenExpanded(path)) {
            DefaultMutableTreeNode node =
                    (DefaultMutableTreeNode) path.getLastPathComponent();
            if (sheet.isMBeanNode(node) && !getTree().hasMetadataNodes(node)) {
            	getTree().addMetadataNodes(node);
            }
        }
    }

    /* tree will expand listener: treeWillCollapse */
    public void treeWillCollapse(TreeExpansionEvent e)
            throws ExpandVetoException {
    }

	private JScrollPane theFilteredScrollPane;
	private JScrollPane theScrollPane;

	private class PromptingTextField extends JTextField implements FocusListener {
		private String prompt;
		boolean promptRemoved = false;
		Color fg;

		public PromptingTextField(String prompt, int columns) {
			super(prompt, columns);

			this.prompt = prompt;
			updateForeground();
			addFocusListener(this);
		}

		@Override
		public void revalidate() {
			super.revalidate();
			updateForeground();
		}

		private synchronized void updateForeground() {
			this.fg = UIManager.getColor("TextField.foreground");
			if (promptRemoved) {
				setForeground(fg);
			} else {
				setForeground(Color.gray);
			}
		}

		public synchronized String getText() {
			if (!promptRemoved) {
				return "";
			} else {
				return super.getText();
			}
		}

		public synchronized void focusGained(FocusEvent e) {
			if (!promptRemoved) {
				promptRemoved = true;
				setText("");
				setForeground(fg);
			}
		}

		public synchronized void focusLost(FocusEvent e) {
			if (promptRemoved && getText().equals("")) {
				promptRemoved = false;
				setText(prompt);
				setForeground(Color.gray);
			}
		}

	}

	@Override
	public void insertUpdate(DocumentEvent e) {
		updateView();
	}

	@Override
	public void removeUpdate(DocumentEvent e) {
		updateView();
	}

	@Override
	public void changedUpdate(DocumentEvent e) {
		//updateView();
	}

	protected boolean isFiltering = false;

	protected void updateView() {
		if (filterTF.getText().isEmpty()) {
			if (isFiltering) {
				isFiltering = false;
				EventQueue.invokeLater(new Runnable() {
					@Override
					public void run() {
						treePanel.removeAll();
						treePanel.add(theScrollPane);
						treePanel.repaint();
					}
				});
				updateLabel("");
			}
		} else if (!filterTF.getText().isEmpty()) {
			if (!isFiltering) {
				isFiltering = true;
				EventQueue.invokeLater(new Runnable() {
					@Override
					public void run() {
						treePanel.removeAll();
						treePanel.add(theFilteredScrollPane);
						treePanel.repaint();
					}
				});
			}
			updateFilter();
		}
	}

	boolean updatePendingForFilter = false;
	private Collection<DefaultMutableTreeNode> mxbeanNodes;
	private JPanel treePanel;

	protected void updateFilter() {
		updatePendingForFilter = true;
		workerAdd(new Runnable() {
			@Override
			public void run() {
				if (updatePendingForFilter) {
					updateLabel("Filtering...");
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
					}
				}
				updatePendingForFilter = false;

				String filterText = filterTF.getText();
				if (filterText.isEmpty()) {
					// Filter cleared, no reason to run.
					return;
				}

				final Pattern pattern = Pattern.compile(".*" + Matcher.quoteReplacement(filterText) + ".*", Pattern.CASE_INSENSITIVE);

				final Set<XMBean> filteredMBeans = new HashSet<XMBean>();

				mxbeanNodes = tree.getXmbeans().values();
				for (DefaultMutableTreeNode mbeanNode : mxbeanNodes) {
					ComparableDefaultMutableTreeNode comparableTreeNode = (ComparableDefaultMutableTreeNode) mbeanNode;
					XNodeInfo nodeInfo = (XNodeInfo) comparableTreeNode.getUserObject();
					XMBean data = (XMBean)nodeInfo.getData();
					if (pattern.matcher(data.getObjectName().toString()).find()) {
						filteredMBeans.add(data);
					}
				}
				

				filteredTree.removeAll();

				filteredTree.setVisible(false);

				for (XMBean mbeanObject : filteredMBeans) {
					filteredTree.addMBeanToView(mbeanObject.getObjectName(), mbeanObject, new Dn(mbeanObject.getObjectName()));
				}

				try {
					// Need to wait until all nodes added to tree.
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				filteredTree.expandAllNodes();

				filteredTree.setVisible(true);
				
				updateLabel("");
			}
		});
	}
	
	protected void updateLabel(final String labelText) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				operationLabel.setText(labelText);
			}
		});
	}
	
	private Thread testerThread = new Thread(new Runnable() {
		@Override
		public void run() {
			try {
				doIt();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		public void doIt()  throws Exception {
			MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
			
			AMXBean a = new AMXBean() {
				@Override
				public int geta() {
					return 0;
				}
			};
			
			while (true) {
				platformMBeanServer.registerMBean(a, new ObjectName("test:name=a"));
				
				Thread.sleep(5000);
				
				platformMBeanServer.unregisterMBean(new ObjectName("test:name=a"));

				Thread.sleep(5000);
			}
			
		}
		
	});
	public interface AMXBean {
		int geta();
	}
}
