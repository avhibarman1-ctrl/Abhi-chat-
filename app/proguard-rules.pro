# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Credential Manager & Google Identity Services classes for Google OAuth
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keepclassmembers class * extends androidx.credentials.Credential { *; }
-keepclassmembers class * extends androidx.credentials.CustomCredential { *; }

# Keep Firebase Auth & Firestore model classes
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class com.example.data.model.** { *; }
-keep @androidx.annotation.Keep class * { *; }
