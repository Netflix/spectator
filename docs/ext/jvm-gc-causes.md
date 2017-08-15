# GC Causes

The various GC causes aren't well documented. The list provided here comes from the
[gcCause.cpp](http://hg.openjdk.java.net/jdk8u/hs-dev/hotspot/file/tip/src/share/vm/gc_interface/gcCause.cpp)
file in the jdk and we include some information on what these mean for the application.

### System.gc__

Something called [System.gc()](http://docs.oracle.com/javase/7/docs/api/java/lang/System.html#gc()).
If you are seeing this once an hour it is likely related to the RMI GC interval. For more
details see:

* [Unexplained System.gc() calls due to Remote Method Invocation (RMI) or explict garbage collections](http://www-01.ibm.com/support/docview.wss?uid=swg21173431)
* [sun.rmi.dgc.client.gcInterval](http://docs.oracle.com/javase/6/docs/technotes/guides/rmi/sunrmiproperties.html)

### FullGCAlot

Most likely you'll never see this value. In debug builds of the jdk there is an option,
`-XX:+FullGCALot`, that will trigger a full GC at a regular interval for testing purposes.

### ScavengeAlot

Most likely you'll never see this value. In debug builds of the jdk there is an option,
`-XX:+ScavengeALot`, that will trigger a minor GC at a regular interval for testing purposes.

### Allocation_Profiler

Prior to java 8 you would see this if running with the `-Xaprof` setting. It would be triggered
just before the jvm exits. The `-Xaprof` option was removed in java 8.

### JvmtiEnv_ForceGarbageCollection

Something called the JVM tool interface function
[ForceGarbageCollection](https://docs.oracle.com/javase/8/docs/platform/jvmti/jvmti.html#ForceGarbageCollection).
Look at the `-agentlib` param to java to see what agents are configured.

### GCLocker_Initiated_GC

The GC locker prevents GC from occurring when JNI code is in a
[critical region](http://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/functions.html#GetPrimitiveArrayCritical_ReleasePrimitiveArrayCritical).
If GC is needed while a thread is in a critical region, then it will allow them to complete,
i.e. call the corresponding release function. Other threads will not be permitted to enter a
critical region. Once all threads are out of critical regions a GC event will be triggered. 

### Heap_Inspection_Initiated_GC

GC was initiated by an inspection operation on the heap. For example you can trigger this
with [jmap](http://docs.oracle.com/javase/7/docs/technotes/tools/share/jmap.html):

`$ jmap -histo:live <pid>`

### Heap_Dump_Initiated_GC

GC was initiated before dumping the heap. For example you can trigger this with
[jmap](http://docs.oracle.com/javase/7/docs/technotes/tools/share/jmap.html):

`$ jmap -dump:live,format=b,file=heap.out <pid>`

Another common example would be clicking the Heap Dump button on the Monitor tab in
[jvisualvm](http://docs.oracle.com/javase/7/docs/technotes/tools/share/jvisualvm.html).

### WhiteBox_Initiated_Young_GC

Most likely you'll never see this value. Used for testing hotspot, it indicates something
called `sun.hotspot.WhiteBox.youngGC()`. 

### No_GC

Used for CMS to indicate concurrent phases.

### Allocation_Failure

Usually this means that there is an allocation request that is bigger than the available space
in young generation and will typically be associated with a minor GC. For G1 this will likely
be a major GC and it is more common to see [G1_Evacuation_Pause](#g1_evacuation_pause) for
routine minor collections.

On linux the jvm will trigger a GC if the kernel indicates there isn't much memory left via
[mem_notify](http://lwn.net/Articles/267013/).

### Tenured_Generation_Full

Not used?

### Permanent_Generation_Full

Triggered as a result of an allocation failure in
[PermGen](https://blogs.oracle.com/poonam/entry/about_g1_garbage_collector_permanent). Pre java 8.

### Metadata_GC_Threshold

Triggered as a result of an allocation failure in
[Metaspace](https://blogs.oracle.com/poonam/entry/about_g1_garbage_collector_permanent).
Metaspace replaced PermGen was added in java 8.

### CMS_Generation_Full

Not used?

### CMS_Initial_Mark

Initial mark phase of CMS, for more details see
[Phases of CMS](https://blogs.oracle.com/jonthecollector/entry/hey_joe_phases_of_cms).
Unfortunately it doesn't appear to be reported via the mbeans and we just get [No_GC](#no_gc).

### CMS_Final_Remark

Remark phase of CMS, for more details see
[Phases of CMS](https://blogs.oracle.com/jonthecollector/entry/hey_joe_phases_of_cms).
Unfortunately it doesn't appear to be reported via the mbeans and we just get [No_GC](#no_gc).

### CMS_Concurrent_Mark

Concurrent mark phase of CMS, for more details see
[Phases of CMS](https://blogs.oracle.com/jonthecollector/entry/hey_joe_phases_of_cms).
Unfortunately it doesn't appear to be reported via the mbeans and we just get [No_GC](#no_gc).

### Old_Generation_Expanded_On_Last_Scavenge

Not used?

### Old_Generation_Too_Full_To_Scavenge

Not used?

### Ergonomics

This indicates you are using the adaptive size policy, `-XX:+UseAdaptiveSizePolicy` and is
on by default for recent versions, with the parallel collector (`-XX:+UseParallelGC`). For
more details see [The Why of GC Ergonomics](https://blogs.oracle.com/jonthecollector/entry/the_unspoken_the_why_of).

### G1_Evacuation_Pause

An evacuation pause is the most common young gen cause for G1 and indicates that it is copying
live objects from one set of regions, young and sometimes young + old, to another set of
regions. For more details see [Understanding G1 GC Logs](https://blogs.oracle.com/poonam/entry/understanding_g1_gc_logs).

### G1_Humongous_Allocation

A humongous allocation is one where the size is greater than 50% of the G1 region size. Before
a humongous allocation the jvm checks if it should do a routine
[evacuation pause](#g1_evacuation_pause) without regard to the actual allocation size, but if
triggered due to this check the cause will be listed as humongous allocation. This cause is
also used for any collections used to free up enough space for the allocation. 

### Last_ditch_collection

For perm gen (java 7 or earlier) and metaspace (java 8+) a last ditch collection will be
triggered if an allocation fails and the memory pool cannot be expanded.

### ILLEGAL_VALUE_-_last_gc_cause_-_ILLEGAL_VALUE

Included for completeness, but you should never see this value.

### unknown_GCCause

Included for completeness, but you should never see this value.
