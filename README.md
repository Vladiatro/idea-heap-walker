# idea-heap-walker
This is an educational plugin for profiling Java application, which will help you to find memory leaks.
It has the following set of runtime metrics:
* Object creation places: one can view the stack frame which was when the object was created.
* Object creation time: when the object was created. When debugger stops, the time counter stops too.
* Object last access time: when one of the object's fields was last time accessed.
Remember that if you invoked object's methods but didn't use its fields, you may not need this object.

These metrics don't work with arrays!

All this data can be aggregated within the instances of a class: you will see charts with the distribution of object lifetimes,
last usage times, the most object generative code lines and methods. These charts can be used for further filtering of objects
in the list. You can view the referring objects using the right mouse button. Also you can find the object from the standard
debugger: right click on it, then 'Show in Heap'.

It's better to perform analysis when the program is suspended on a breakpoint.

### Installing the plugin
* Download the plugin [here](http://falsetrue.net/idea-profiler.jar).
* Copy the file to the .IntelliJIDEAxx/config/plugins folder, and then (re)start the IDE.
* Select File | Settings.
* Under IDE Settings, click Plugins.
* In the Plugins area, open the Installed tab, and then select the check-box next to 'Simple Java Profiler'.
* Click OK, restart the IDE.

You can disable the plugin using the same way - just un-select that check-box.

### Running the plugin
* Create a Java project.
* Open 'Memory content' tab in the bottom.
* Put a breakpoint somewhere in the program (recommended).
* Start debugging it.
* Select a class on the left table, then click a button with a bug to switch on the object creation monitoring.
* Remove the breakpoint, resume the program.
* You can watch loaded classes, instances' toStrings()' and creation stack frames/diagrams when the program is running.
Try clicking on a class, switching between tabs on the right panel and clicking on the charts.
* After some time put a breakpoint again.
* Now you can see when an object was last time used (if you turned on the monitoring) using a colorful square.
The greener = the better, yellower means a big older, then goes red and the black, which are the oldest one.
If the object haven't been used since monitoring start, it's black too. Also you can see the distribution of last usage
in the 'Usage' tab on the right panel.
