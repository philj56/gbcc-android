-keep class com.philj56.gbcc.SettingsFragment {
	<init>();
}

#-dontobfuscate

# For readable stack traces
-renamesourcefileattribute SourceFile
-keepattributes SourceFile, LineNumberTable

# Allows some shrinking, safe when not making a library?
-repackageclasses ''
-allowaccessmodification