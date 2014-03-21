baselining-plugin
=================

This is a maven plugin that helps you enforcing semantic versioning as recommended by OSGi,

A simple configuration would look like this

 <plugin>
     <groupId>com.santiagozky.baselining</groupId>
     <artifactId>baselining-plugin</artifactId>
     <version>0.0.1</version>
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
