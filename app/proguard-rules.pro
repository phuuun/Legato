# Keep names readable: Legato is open source, and stack traces should say where they broke.
-dontobfuscate

# NewPipeExtractor runs YouTube's player JavaScript through Rhino, which finds its own classes by reflection.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn org.slf4j.**

# TagLib is native: its JNI code calls back into these classes by name.
-keep class com.kyant.taglib.** { *; }
