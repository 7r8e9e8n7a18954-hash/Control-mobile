# The app does not currently enable minification. Keep serialization metadata safe
# if minification is enabled in the future.
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.universalaiagent.**$$serializer { *; }
-keepattributes *Annotation*, InnerClasses
