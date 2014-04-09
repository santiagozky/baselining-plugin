baselining-plugin
=================

This is a maven plugin that helps you enforcing semantic versioning as recommended by OSGi, Requires maven 3.1.0

If you need support for a maven version lower than 3.1.0, please check the maven-3.0-compatibility branch.

A simple configuration would look like this
```XML
 <plugin>
     <groupId>com.santiagozky.baselining</groupId>
     <artifactId>baselining-plugin</artifactId>
     <version>1.0.0-SNAPSHOT</version>
		 <executions>
			    <execution>
			        <id>execution1</id>
			        <phase>verify</phase>
					  	<configuration>
						        <strict>true</strict>
						  </configuration>
			        <goals>
			           <goal>baseline</goal>
			         </goals>
			     </execution>
			</executions>
</plugin>
```
If the strict option is true, the build will fail if a package or the bundle have a version that is lower than the recommended


This software is covered by the Apache license v2. See the LICENSE file for more information.