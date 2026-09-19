# Consumer ProGuard rules for the SDK

# networknt creates schema keyword validators by reflection. Keep their constructor
# signatures in host apps' minified release builds, not just in this library's AAR.
-keep class com.networknt.schema.** implements com.networknt.schema.JsonValidator { *; }
-keepclassmembers class com.networknt.schema.** {
    public <init>(...);
}

# Optional Joni backend is not packaged or enabled; the SDK uses Java regex.
-dontwarn org.joni.**
-dontwarn org.jcodings.**
