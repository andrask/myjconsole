/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * Modified: Andras Kovi 2012.
 */

package sun.tools.jconsole;

import static sun.tools.jconsole.Resources.getMnemonicInt;
import static sun.tools.jconsole.Resources.getText;
import static sun.tools.jconsole.Utilities.setAccessibleName;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import andrask.jconsole.utils.CustomThreadInfo;
import andrask.sun.tools.jconsole.Settings;

@SuppressWarnings("serial")
class ThreadTab extends Tab implements ActionListener, DocumentListener, ListSelectionListener {
    PlotterPanel threadMeter;
    TimeComboBox timeComboBox;
    JTabbedPane threadListTabbedPane;
    DefaultListModel listModel;
    JTextField filterTF;
    JLabel messageLabel;
    JSplitPane threadsSplitPane;
    HashMap<Long, ThreadInfo> nameCache = new HashMap<Long, ThreadInfo>();

    private ThreadOverviewPanel overviewPanel;
    private boolean plotterListening = false;


    private static final String threadCountKey   = "threadCount";
    private static final String peakKey          = "peak";

    private static final String threadCountName   = Resources.getText("Live Threads");
    private static final String peakName          = Resources.getText("Peak");

    private static final Color  threadCountColor = Plotter.defaultColor;
    private static final Color  peakColor        = Color.red;

    private static final Border thinEmptyBorder  = new EmptyBorder(2, 2, 2, 2);

    private static final String infoLabelFormat = "ThreadTab.infoLabelFormat";


    /*
      Hierarchy of panels and layouts for this tab:

        ThreadTab (BorderLayout)

            North:  topPanel (BorderLayout)

                        Center: controlPanel (FlowLayout)
                                    timeComboBox

            Center: sliptPane
            			TOP	plotterPanel (BorderLayout)
            					Center: plotter
            			BOTTOM threadListTabbedPane
            					Tab: Threads
            					Tab: Stack traces

    */


    public static String getTabName() {
        return Resources.getText("Threads");
    }

    public ThreadTab(VMPanel vmPanel) {
        super(vmPanel, getTabName());

        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(4, 4, 3, 4));

        JPanel topPanel     = new JPanel(new BorderLayout());
        JPanel plotterPanel = new JPanel(new VariableGridLayout(0, 1, 4, 4, true, true));

        add(topPanel, BorderLayout.NORTH);
        add(plotterPanel,  BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        topPanel.add(controlPanel, BorderLayout.CENTER);

        threadMeter = new PlotterPanel(Resources.getText("Number of Threads"),
                                       Plotter.Unit.NONE, true);
        threadMeter.plotter.createSequence(threadCountKey, threadCountName,  threadCountColor, true);
        threadMeter.plotter.createSequence(peakKey,        peakName,         peakColor,        true);
        setAccessibleName(threadMeter.plotter,
                          getText("ThreadTab.threadPlotter.accessibleName"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        plotterPanel.add(splitPane, BorderLayout.CENTER);
        splitPane.setDividerLocation(Settings.getInt(Settings.KEY_THREADS_PLOT_SPLITTERPOSITION, 100));
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        splitPane.add(threadMeter, JSplitPane.TOP, 0);

        timeComboBox = new TimeComboBox(threadMeter.plotter);
        controlPanel.add(new LabeledComponent(Resources.getText("Time Range:"),
                                              getMnemonicInt("Time Range:"),
                                              timeComboBox));

        listModel = new DefaultListModel();

        JTextArea textArea = new JTextArea();
        textArea.setBorder(thinEmptyBorder);
        textArea.setEditable(false);
        setAccessibleName(textArea,
                          getText("ThreadTab.threadInfo.accessibleName"));
        JList list = new ThreadJList(listModel, textArea);

        Dimension di = new Dimension(super.getPreferredSize());
        di.width = Math.min(di.width, 200);

        JScrollPane threadlistSP = new JScrollPane(list);
        threadlistSP.setPreferredSize(di);
        threadlistSP.setBorder(null);

        JScrollPane textAreaSP = new JScrollPane(textArea);
        textAreaSP.setBorder(null);

        threadListTabbedPane = new JTabbedPane(JTabbedPane.TOP);
        threadsSplitPane  = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                           threadlistSP, textAreaSP);
        threadsSplitPane.setOneTouchExpandable(true);
        threadsSplitPane.setBorder(null);

        threadListTabbedPane.addTab(Resources.getText("Threads"), getThreadsPanel());

        threadListTabbedPane.addTab("Stack traces", getStackTracesPanel());

        splitPane.add(threadListTabbedPane, JSplitPane.BOTTOM, 0);
    }

	private JPanel getStackTracesPanel() {
		JPanel secondTabPanel = new JPanel(new BorderLayout());
        secondTabPanel.setOpaque(false);
        
        JPanel tabToolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        tabToolPanel.setOpaque(false);

        JButton getStackTracesButton = new JButton("Get stack traces");
        getStackTracesButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshStackTraces();
			}
		});
        tabToolPanel.add(getStackTracesButton);

        stackTraceDisplay = new JTextArea();
        JScrollPane stackTraceDisplayScrollPane = new JScrollPane(stackTraceDisplay);
        
        secondTabPanel.add(stackTraceDisplayScrollPane, BorderLayout.CENTER);
        secondTabPanel.add(tabToolPanel, BorderLayout.SOUTH);
        
		return secondTabPanel;
	}

    private Component getThreadsPanel() {
        JPanel firstTabPanel = new JPanel(new BorderLayout());
        firstTabPanel.setOpaque(false);

        JPanel firstTabToolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        firstTabToolPanel.setOpaque(false);

        filterTF = new PromptingTextField("Filter", 20);
        filterTF.getDocument().addDocumentListener(this);
        firstTabToolPanel.add(filterTF);

        JSeparator separator = new JSeparator(JSeparator.VERTICAL);
        separator.setPreferredSize(new Dimension(separator.getPreferredSize().width,
                                                 filterTF.getPreferredSize().height));
        firstTabToolPanel.add(separator);

        JButton detectDeadlockButton = new JButton(Resources.getText("Detect Deadlock"));
        detectDeadlockButton.setMnemonic(getMnemonicInt("Detect Deadlock"));
        detectDeadlockButton.setActionCommand("detectDeadlock");
        detectDeadlockButton.addActionListener(this);
        detectDeadlockButton.setToolTipText(getText("Detect Deadlock.toolTip"));
        firstTabToolPanel.add(detectDeadlockButton);

        messageLabel = new JLabel();
        firstTabToolPanel.add(messageLabel);

        firstTabPanel.add(threadsSplitPane, BorderLayout.CENTER);
        firstTabPanel.add(firstTabToolPanel, BorderLayout.SOUTH);
        
        return firstTabPanel;
    }
    
	private void refreshStackTraces() {
		workerAdd(new Runnable() {
			public void run() {
				ProxyClient proxyClient = vmPanel.getProxyClient();
				final StringBuilder sb = new StringBuilder();
				ThreadMXBean threadMBean;
				try {
					threadMBean = proxyClient.getThreadMXBean();
					ThreadInfo[] infos = threadMBean.dumpAllThreads(true, true);
					for (ThreadInfo t : infos) {
						sb.append(new CustomThreadInfo(t).toString());
					}
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							stackTraceDisplay.setText(sb.toString());
						}
					});
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}
    
    private long oldThreads[] = new long[0];

    public SwingWorker<?, ?> newSwingWorker() {
        final ProxyClient proxyClient = vmPanel.getProxyClient();

        if (!plotterListening) {
            proxyClient.addWeakPropertyChangeListener(threadMeter.plotter);
            plotterListening = true;
        }

        return new SwingWorker<Boolean, Object>() {
            private int tlCount;
            private int tpCount;
            private long ttCount;
            private long[] threadIds;
            private long timeStamp;

            public Boolean doInBackground() {
                try {
                    ThreadMXBean threadMBean = proxyClient.getThreadMXBean();

                    tlCount = threadMBean.getThreadCount();
                    tpCount = threadMBean.getPeakThreadCount();
                    if (overviewPanel != null) {
                        ttCount = threadMBean.getTotalStartedThreadCount();
                    } else {
                        ttCount = 0L;
                    }

                    threadIds = threadMBean.getAllThreadIds();
                    long[] newThreadIds = new long[threadIds.length];
                    int numNewThreads = 0;
                    for (long newThread : threadIds) {
                        if (nameCache.get(newThread) == null) {
                        	newThreadIds[numNewThreads++] = newThread;
                        }
                    }
                    
                    long[] newThreadIdsTrimmed = new long[numNewThreads];
                    System.arraycopy(newThreadIds, 0, newThreadIdsTrimmed, 0, numNewThreads);
                    
                    ThreadInfo[] threadInfos = threadMBean.getThreadInfo(newThreadIdsTrimmed);
                    
					for (ThreadInfo threadInfo : threadInfos) {
						if (threadInfo != null) {
							String name = threadInfo.getThreadName();
							if (name != null) {
								nameCache.put(threadInfo.getThreadId(), threadInfo);
							}
						}
					}
                    timeStamp = System.currentTimeMillis();
                    return true;
                } catch (IOException e) {
                    return false;
                } catch (UndeclaredThrowableException e) {
                    return false;
                }
            }

            protected void done() {
                try {
                    if (!get()) {
                        return;
                    }
                } catch (InterruptedException ex) {
                    return;
                } catch (ExecutionException ex) {
                    if (JConsole.isDebug()) {
                        ex.printStackTrace();
                    }
                    return;
                }

                threadMeter.plotter.addValues(timeStamp, tlCount, tpCount);
                threadMeter.setValueLabel(tlCount+"");

                if (overviewPanel != null) {
                    overviewPanel.updateThreadsInfo(tlCount, tpCount, ttCount, timeStamp);
                }

                String filter = filterTF.getText().toLowerCase(Locale.ENGLISH);
                boolean doFilter = (filter.length() > 0);

                ArrayList<Long> threadIdList = new ArrayList<Long>();
                for (long t : threadIds) {
                    threadIdList.add(t);
                }
                Iterator<Long> iterator = threadIdList.iterator();
                while (iterator.hasNext()) {
                    long newThread = iterator.next();
                    ThreadInfo threadInfo = nameCache.get(newThread);
                    String name = getThreadListLabel(threadInfo);
                    if (doFilter && name != null &&
                        name.toLowerCase(Locale.ENGLISH).indexOf(filter) < 0) {

                        iterator.remove();
                    }
                }
                long[] newThreads = threadIds;
                if (threadIdList.size() < threadIds.length) {
                    newThreads = new long[threadIdList.size()];
                    for (int i = 0; i < newThreads.length; i++) {
                        newThreads[i] = threadIdList.get(i);
                    }
                }


                for (long oldThread : oldThreads) {
                    boolean found = false;
                    for (long newThread : newThreads) {
                        if (newThread == oldThread) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        listModel.removeElement(oldThread);
                        if (!doFilter) {
                            nameCache.remove(oldThread);
                        }
                    }
                }

                // Threads are in reverse chronological order
                for (int i = newThreads.length - 1; i >= 0; i--) {
                    long newThread = newThreads[i];
                    boolean found = false;
                    for (long oldThread : oldThreads) {
                        if (newThread == oldThread) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        listModel.addElement(newThread);
                    }
                }
                oldThreads = newThreads;
            }
        };
    }

    long lastSelected = -1;
	private JTextArea stackTraceDisplay;

    public void valueChanged(ListSelectionEvent ev) {
        ThreadJList list = (ThreadJList)ev.getSource();
        final JTextArea textArea = list.textArea;

        Long selected = (Long)list.getSelectedValue();
        if (selected == null) {
            if (lastSelected != -1) {
                selected = lastSelected;
            }
        } else {
            lastSelected = selected;
        }
        textArea.setText("");
        if (selected != null) {
            final long threadID = selected;
            workerAdd(new Runnable() {
                public void run() {
                    ProxyClient proxyClient = vmPanel.getProxyClient();
                    StringBuilder sb = new StringBuilder();
                    try {
                        ThreadMXBean threadMBean = proxyClient.getThreadMXBean();
                        ThreadInfo ti = null;
                        MonitorInfo[] monitors = null;
                        if (proxyClient.isLockUsageSupported() &&
                              threadMBean.isObjectMonitorUsageSupported()) {
                            // VMs that support the monitor usage monitoring
                            ThreadInfo[] infos = threadMBean.getThreadInfo(new long[] {threadID}, true, false);
                            for (ThreadInfo info : infos) {
                                if (info.getThreadId() == threadID) {
                                    ti = info;
                                    monitors = info.getLockedMonitors();
                                    break;
                                }
                            }
                        } else {
                            // VM doesn't support monitor usage monitoring
                            ti = threadMBean.getThreadInfo(threadID, Integer.MAX_VALUE);
                        }
                        if (ti != null) {
                            if (ti.getLockName() == null) {
                                sb.append(Resources.getText("Name State",
                                              ti.getThreadName(),
                                              ti.getThreadState().toString()));
                            } else if (ti.getLockOwnerName() == null) {
                                sb.append(Resources.getText("Name State LockName",
                                              ti.getThreadName(),
                                              ti.getThreadState().toString(),
                                              ti.getLockName()));
                            } else {
                                sb.append(Resources.getText("Name State LockName LockOwner",
                                              ti.getThreadName(),
                                              ti.getThreadState().toString(),
                                              ti.getLockName(),
                                              ti.getLockOwnerName()));
                            }
                            sb.append(Resources.getText("BlockedCount WaitedCount",
                                              ti.getBlockedCount(),
                                              ti.getWaitedCount()));
                            sb.append(Resources.getText("Stack trace"));
                            int index = 0;
                            for (StackTraceElement e : ti.getStackTrace()) {
                                sb.append(e.toString()+"\n");
                                if (monitors != null) {
                                    for (MonitorInfo mi : monitors) {
                                        if (mi.getLockedStackDepth() == index) {
                                            sb.append(Resources.getText("Monitor locked", mi.toString()));
                                        }
                                    }
                                }
                                index++;
                            }
                        }
                    } catch (IOException ex) {
                        // Ignore
                    } catch (UndeclaredThrowableException e) {
                        proxyClient.markAsDead();
                    }
                    final String text = sb.toString();
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            textArea.setText(text);
                            textArea.setCaretPosition(0);
                        }
                    });
                }
            });
        }
    }

    private String getThreadListLabel(ThreadInfo threadInfo) {
    	return threadInfo.getThreadId() + "\t " + threadInfo.getThreadName();
    }
    
    private void doUpdate() {
        workerAdd(new Runnable() {
            public void run() {
                update();
            }
        });
    }


    private void detectDeadlock() {
        workerAdd(new Runnable() {
            public void run() {
                try {
                    final Long[][] deadlockedThreads = getDeadlockedThreadIds();

                    if (deadlockedThreads == null || deadlockedThreads.length == 0) {
                        // Display message for 30 seconds. Do it on a separate thread so
                        // the sleep won't hold up the worker queue.
                        // This will be replaced later by separate statusbar logic.
                        new Thread() {
                            public void run() {
                                try {
                                    SwingUtilities.invokeAndWait(new Runnable() {
                                        public void run() {
                                            String msg = Resources.getText("No deadlock detected");
                                            messageLabel.setText(msg);
                                            threadListTabbedPane.revalidate();
                                        }
                                    });
                                    sleep(30 * 1000);
                                } catch (InterruptedException ex) {
                                    // Ignore
                                } catch (InvocationTargetException ex) {
                                    // Ignore
                                }
                                SwingUtilities.invokeLater(new Runnable() {
                                    public void run() {
                                        messageLabel.setText("");
                                    }
                                });
                            }
                        }.start();
                        return;
                    }

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            // Remove old deadlock tabs
                            while (threadListTabbedPane.getTabCount() > 2) {
                                threadListTabbedPane.removeTabAt(2);
                            }

                            if (deadlockedThreads != null) {
                                for (int i = 0; i < deadlockedThreads.length; i++) {
                                    DefaultListModel listModel = new DefaultListModel();
                                    JTextArea textArea = new JTextArea();
                                    textArea.setBorder(thinEmptyBorder);
                                    textArea.setEditable(false);
                                    setAccessibleName(textArea,
                                        getText("ThreadTab.threadInfo.accessibleName"));
                                    JList list = new ThreadJList(listModel, textArea);
                                    JScrollPane threadlistSP = new JScrollPane(list);
                                    JScrollPane textAreaSP = new JScrollPane(textArea);
                                    threadlistSP.setBorder(null);
                                    textAreaSP.setBorder(null);
                                    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                                                                 threadlistSP, textAreaSP);
                                    splitPane.setOneTouchExpandable(true);
                                    splitPane.setBorder(null);
                                    splitPane.setDividerLocation(threadsSplitPane.getDividerLocation());
                                    String tabName;
                                    if (deadlockedThreads.length > 1) {
                                        tabName = Resources.getText("deadlockTabN", i+1);
                                    } else {
                                        tabName = Resources.getText("deadlockTab");
                                    }
                                    threadListTabbedPane.addTab(tabName, splitPane);

                                    for (long t : deadlockedThreads[i]) {
                                        listModel.addElement(t);
                                    }
                                }
                                threadListTabbedPane.setSelectedIndex(1);
                            }
                        }
                    });
                } catch (IOException e) {
                    // Ignore
                } catch (UndeclaredThrowableException e) {
                    vmPanel.getProxyClient().markAsDead();
                }
            }
        });
    }


    // Return deadlocked threads or null
    public Long[][] getDeadlockedThreadIds() throws IOException {
        ProxyClient proxyClient = vmPanel.getProxyClient();
        ThreadMXBean threadMBean = proxyClient.getThreadMXBean();

        long[] ids = proxyClient.findDeadlockedThreads();
        if (ids == null) {
            return null;
        }
        ThreadInfo[] infos = threadMBean.getThreadInfo(ids, Integer.MAX_VALUE);

        List<Long[]> dcycles = new ArrayList<Long[]>();
        List<Long> cycle = new ArrayList<Long>();

        // keep track of which thread is visited
        // one thread can only be in one cycle
        boolean[] visited = new boolean[ids.length];

        int deadlockedThread = -1; // Index into arrays
        while (true) {
            if (deadlockedThread < 0) {
                if (cycle.size() > 0) {
                    // a cycle found
                    dcycles.add(cycle.toArray(new Long[0]));
                    cycle = new ArrayList<Long>();
                }
                // start a new cycle from a non-visited thread
                for (int j = 0; j < ids.length; j++) {
                    if (!visited[j]) {
                        deadlockedThread = j;
                        visited[j] = true;
                        break;
                    }
                }
                if (deadlockedThread < 0) {
                    // done
                    break;
                }
            }

            cycle.add(ids[deadlockedThread]);
            long nextThreadId = infos[deadlockedThread].getLockOwnerId();
            for (int j = 0; j < ids.length; j++) {
                ThreadInfo ti = infos[j];
                if (ti.getThreadId() == nextThreadId) {
                     if (visited[j]) {
                         deadlockedThread = -1;
                     } else {
                         deadlockedThread = j;
                         visited[j] = true;
                     }
                     break;
                }
            }
        }
        return dcycles.toArray(new Long[0][0]);
    }





    // ActionListener interface
    public void actionPerformed(ActionEvent evt) {
        String cmd = ((AbstractButton)evt.getSource()).getActionCommand();

        if (cmd == "detectDeadlock") {
            messageLabel.setText("");
            detectDeadlock();
        }
    }



    // DocumentListener interface

    public void insertUpdate(DocumentEvent e) {
        doUpdate();
    }

    public void removeUpdate(DocumentEvent e) {
        doUpdate();
    }

    public void changedUpdate(DocumentEvent e) {
        doUpdate();
    }



    private class ThreadJList extends JList {
        private JTextArea textArea;

        ThreadJList(DefaultListModel listModel, JTextArea textArea) {
            super(listModel);

            this.textArea = textArea;

            setBorder(thinEmptyBorder);

            addListSelectionListener(ThreadTab.this);
            setCellRenderer(new DefaultListCellRenderer() {
            	@Override
                public Component getListCellRendererComponent(JList list, Object value, int index,
                                                              boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                    if (value != null) {
                    	String name = value.toString();
                        ThreadInfo threadInfo = nameCache.get(value);
                        if (threadInfo != null) {
                        	name = getThreadListLabel(threadInfo);
                        }
                        setText(name);
                    }
                    return this;
                }
            });
        }

        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.width = Math.max(d.width, 100);
            return d;
        }
    }

    private class PromptingTextField extends JTextField implements FocusListener {
        private String prompt;
        boolean promptRemoved = false;
        Color fg;

        public PromptingTextField(String prompt, int columns) {
            super(prompt, columns);

            this.prompt = prompt;
            updateForeground();
            addFocusListener(this);
            setAccessibleName(this, prompt);
        }

        @Override
        public void revalidate() {
            super.revalidate();
            updateForeground();
        }

        private void updateForeground() {
            this.fg = UIManager.getColor("TextField.foreground");
            if (promptRemoved) {
                setForeground(fg);
            } else {
                setForeground(Color.gray);
            }
        }

        public String getText() {
            if (!promptRemoved) {
                return "";
            } else {
                return super.getText();
            }
        }

        public void focusGained(FocusEvent e) {
            if (!promptRemoved) {
                setText("");
                setForeground(fg);
                promptRemoved = true;
            }
        }

        public void focusLost(FocusEvent e) {
            if (promptRemoved && getText().equals("")) {
                setText(prompt);
                setForeground(Color.gray);
                promptRemoved = false;
            }
        }

    }

    OverviewPanel[] getOverviewPanels() {
        if (overviewPanel == null) {
            overviewPanel = new ThreadOverviewPanel();
        }
        return new OverviewPanel[] { overviewPanel };
    }


    private static class ThreadOverviewPanel extends OverviewPanel {
        ThreadOverviewPanel() {
            super(getText("Threads"), threadCountKey, threadCountName, null);
        }

        private void updateThreadsInfo(long tlCount, long tpCount, long ttCount, long timeStamp) {
            getPlotter().addValues(timeStamp, tlCount);
            getInfoLabel().setText(getText(infoLabelFormat, tlCount, tpCount, ttCount));
        }
    }
}
