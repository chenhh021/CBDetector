diff --git a/lucene/luke/src/distribution/README.md b/lucene/luke/src/distribution/README.md
index 40e55b3..0548fd1 100644
--- a/lucene/luke/src/distribution/README.md
+++ b/lucene/luke/src/distribution/README.md
@@ -27,7 +27,7 @@
 or, using Java modules:
 
 ```
-java --module-path . --add-modules org.apache.logging.log4j --module org.apache.lucene.luke
+java --module-path . --add-modules jdk.unsupported,org.apache.logging.log4j --module org.apache.lucene.luke
 ```
 
 Happy index hacking!
