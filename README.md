# Delombok-plugin
Run delombok from maven. 
Requires at least Java 11.
Kind of slow as it copies the output one more time, but it does the job.

### Example
```xml
<plugin>
    <groupId>com.github.auties00</groupId>
    <artifactId>delombok-plugin</artifactId>
    <version>1.18.24</version>
    <configuration>
        <rootDirectory>${project.build.sourceDirectory}</rootDirectory>
        <outputDirectory>somewhere</outputDirectory>
        <excludedFiles>
            <excludedFile>module-info.java</excludedFile>
        </excludedFiles>
        <parameters>
            <encoding>UTF-8</encoding>
        </parameters>
    </configuration>
    <executions>
        <execution>
            <phase>process-sources</phase>
            <goals>
                <goal>delombok</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```