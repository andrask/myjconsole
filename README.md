myjconsole
==========

Eliminate the annoying featurelessness of Jconsole

- Features:

- MBean tab:
 - save splitter position (feature #1)
 - (TODO) open mbean tree to the first non singular level (where there is more than one item registered)
 - (TODO) open all mbeans down to the Attributes and Operation level (* key on keypad)

- Threads view
 - find threads that are locked on Future.get() calls

In Eclipse on MacOS modify the Java Runtime to include the {javahome}/libs/tools.jar and jconsole.jar.
In windows this is taken care of by maven.

How was this created:
 - downloaded OpenJdk source from http://download.java.net/openjdk/jdk6/promoted/b25/openjdk-6-src-b25-01_may_2012.tar.gz
 - copied the jdk/src/share/classes/sun/jconsole directory to the src
 - moved the packages under andrask.*
 - added maven

Properties are saved in .myjconsole.properties file.

Features:

Feature #1: Splitter position is saved in properties file (see Settings.java and MBeanTab.java)