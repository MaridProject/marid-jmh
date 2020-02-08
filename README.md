maven-jmh
===

This library is intended to run JMH benchmarks in any JVM language without
resources and classes generation in compile time.

The only thing you need is to run ```Jmh.start(...)``` methods passing Options and benchmark classes.

This library

 * creates temporary directory in ```${java.io.tmpdir}``` with prefix ```jmh``` to
   compile all generated by JMH java sources to
 * compiles all generated by JMH java sources by system java compiler
 * patches CompilerHints class to provide a proper path to the compiler hints file
 * patches Runner instance to provide a proper path to BenchmarkList file
 * temporarily replaces java.class.path property with an extended one (by appending temporary directory
   to the end)
 * runs the Runner and returns RunResult collection
 
Maven Central coordinates:
```xml
<dependency>
    <groupId>org.marid</groupId>
    <artifactId>marid-jmh</artifactId>
    <version>1.0</version>
</dependency>
``` 

Requirements
===

Java: 11 or later
JMH: >= 1.20

```xml
<dependency>
   <groupId>org.openjdk.jmh</groupId>
   <artifactId>jmh-generator-reflection</artifactId>
   <version><!-- PLACE YOUR PREFERRED JMH VERSION HERE --></version>
</dependency>
```