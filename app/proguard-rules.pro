-keep class com.philj56.gbcc.SettingsFragment {
	*;
}

-keep class com.philj56.gbcc.RomConfigFragment {
	*;
}

#-dontobfuscate

# For readable stack traces
-renamesourcefileattribute SourceFile
-keepattributes SourceFile, LineNumberTable

# Allows some shrinking, safe when not making a library?
-repackageclasses ''
-allowaccessmodification