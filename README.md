baselining-plugin
=================

This is a maven plugin that helps you enforcing semantic versioning as recommended by OSGi,

A simple configuration would look like this
```XML
 <plugin>
     <groupId>com.santiagozky.baselining</groupId>
     <artifactId>baselining-plugin</artifactId>
     <version>1.0-SNAPSHOT</version>
		 <executions>
			    <execution>
			        <id>execution1</id>
			        <phase>verify</phase>
					  	<configuration>
						        <pedant>true</pedant>
						  </configuration>
			        <goals>
			           <goal>baseline</goal>
			         </goals>
			     </execution>
			</executions>
</plugin>
```
If the pedant option is true, the build will fail if a package or the bundle have a version that is lower than the recommended