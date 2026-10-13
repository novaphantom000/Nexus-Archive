# Release shrinking rules. The base build has no reflection-based libraries,
# so no extra keep rules are required yet.

# Archive libraries reference optional compressors (zstd, brotli, lz4, OSGi, ASM) that are not bundled.
-dontwarn org.apache.commons.compress.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
-dontwarn org.tukaani.xz.**
-dontwarn net.lingala.zip4j.**
