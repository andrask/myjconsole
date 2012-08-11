myjconsole
==========

Eliminate the annoying featurelessness of Jconsole

- Features:

- MBean tab:
 - save splitter position (feature #1)
 - open all mbeans down to the Attributes and Operation level (* key on keypad)
 - open all mbeans on unanimous path 
 - (TODO) return value listing for operations
 - (TODO) do not swallow any exceptions from operations
 - (TODO) exception return value dialog should be copyable

- Threads view
 - (TODO) find threads that are locked on Future.get() calls
 - thread stack traces tab
 
How was this created:
 - downloaded OpenJdk source from http://download.java.net/openjdk/jdk6/promoted/b25/openjdk-6-src-b25-01_may_2012.tar.gz
 - copied the jdk/src/share/classes/sun/jconsole directory to the src
 - moved the packages under andrask.*
 - added maven

Properties are saved in .myjconsole.properties file in the home directory.

Features:

Feature #1: Splitter position is saved in properties file (see Settings.java and MBeanTab.java)