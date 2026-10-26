# Keep names readable: Legato is open source, and stack traces should say where they broke.
-dontobfuscate

# NewPipeExtractor runs YouTube's player JavaScript through Rhino, which finds its own classes by reflection.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
# Its protobuf messages (audio track tags, playlist pages) are filled by reflection on field names like key_;
# R8 saw them never written and dropped them, so fetching threw in release while debug worked.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn org.mozilla.javascript.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn org.slf4j.**

# TagLib is native: its JNI code calls back into these classes by name.
-keep class com.kyant.taglib.** { *; }
