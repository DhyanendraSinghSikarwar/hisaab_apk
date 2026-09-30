# R8 full mode is on (AGP default). Only what reflection or JNI needs is kept here.

# OpenCSV: we use CSVReader/CSVWriter directly, not bean binding.
-dontwarn java.beans.**
-dontwarn org.apache.commons.beanutils.**
-dontwarn org.apache.commons.collections4.**
-dontwarn org.apache.commons.text.**

# Ktor's Android engine references optional JVM classes.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# Keep line numbers for readable crash traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
