# GeekVPN release rules (R8). See docs/geekvpn.md, "Release".

# Readable crash stacks: keep line numbers, hide the file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gson reads generic types and annotations by reflection.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# v2rayNG: its DTOs and settings go through Gson by reflection, its services and
# receivers are named in the manifest, and TProxyService is called from the hev
# JNI library. Upstream is not R8-audited, so its code is kept whole; shrinking
# still drops the unused parts of the libraries around it.
-keep class com.v2ray.ang.** { *; }

# gomobile bindings: libv2ray (Xray core) and cfscan, with the go.* runtime,
# are called from and into native code by name.
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keep class cfscan.** { *; }

# MMKV calls back into Java from native code.
-keep class com.tencent.mmkv.** { *; }

# GeekVPN: API models and everything stored in MMKV are Gson classes. Their
# field names are the stored and wire format, so they must not be renamed, and
# Gson needs their constructors. Methods may still be shrunk and renamed.
-keep class com.geekvpn.** {
    <fields>;
    <init>(...);
}
