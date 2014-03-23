package com.santiagozky.baselining;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import aQute.bnd.differ.Baseline;
import aQute.bnd.differ.Baseline.BundleInfo;
import aQute.bnd.differ.DiffPluginImpl;
import aQute.bnd.differ.Baseline.Info;
import aQute.bnd.osgi.Jar;
import aQute.bnd.version.Version;

/**
 * Says "Hi" to the user.
 * 
 */
@Mojo(name = "baseline",defaultPhase= LifecyclePhase.VERIFY)
@Execute(phase=LifecyclePhase.VERIFY, goal="baseline")
public class BaselineMojo extends AbstractMojo {
	private static final String EXTENSION = ".jar";

	@Parameter(defaultValue = "${project.build.directory}")
	private String target;
	@Parameter(defaultValue = "${pom.version}")
	private String version;

	@Parameter(defaultValue = "${pom.groupId}")
	private String groupId;

	@Parameter(defaultValue = "${pom.artifactId}")
	private String name;

	@Parameter(defaultValue = "${project.build.finalName}")
	private String jarName;

	@Parameter(defaultValue = "${settings.localRepository}")
	private String repoPath;
	
	@Parameter
	private boolean pedant = false;

	public void execute() throws MojoExecutionException {

		String groupPath = groupId.replaceAll("\\.", File.separator);
		File oldJarPath = new File(repoPath, groupPath);
		oldJarPath = new File(oldJarPath, name);
		// we need to get the newest version that is lower than the current one!
		ComparableVersion previousVersion = acquirePreviousVersion(oldJarPath);

		oldJarPath = new File(oldJarPath, previousVersion.toString());
		String oldJarArtifact= name.concat("-").concat(previousVersion.toString().concat(EXTENSION));
		File oldJarName = new File(oldJarPath,oldJarArtifact);

	
		File newJarName = new File(target, jarName.concat(EXTENSION));
		getLog().info("Comparing artifact against "+oldJarArtifact);

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline;
		try {
			baseline = new Baseline(new SimpleReporter(), differ);
			Jar old = new Jar(oldJarName);
			Jar current = new Jar(newJarName);

			Set<Info> infos = baseline.baseline(current, old, null);
			for (Info info : infos) {
				Version v = info.suggestedVersion;
				getLog().info("package "+info.packageName+
						" version changed from " + info.olderVersion
								+ " to " + info.newerVersion);
				if (info.mismatch) {
					 if(pedant){
						 throw new MojoFailureException("wrong version for package "+info.packageName);
					 }
					getLog().error(
							"package "+info.packageName+" version is incorrect. Version should be at least " + v);
				}
				if (info.warning != null && info.warning.length() > 0) {
					getLog().warn("package "+info.packageName+": "+info.warning);
				}

			}
			BundleInfo binfo = baseline.getBundleInfo();
			if (binfo.mismatch) {
				 if(pedant){
					 throw new MojoFailureException("wrong version for artifact");
				 }
				getLog().warn(
						"Bundle version is " + binfo.version
								+ ". The recommended version is "
								+ binfo.suggestedVersion);
			}

		} catch (IOException e) {
			throw new MojoExecutionException("could not calculate  versions", e);
		} catch (Exception e) {
			throw new MojoExecutionException("could not calculate  versions", e);
		}

	}

	private ComparableVersion acquirePreviousVersion(File oldJarPath) {
		String[] directories = oldJarPath.list(new FilenameFilter() {

			public boolean accept(File current, String name) {

				return new File(current, name).isDirectory();
			}
		});

		Arrays.sort(directories);
		ComparableVersion currentVersion = new ComparableVersion(version);
		ComparableVersion previousVersion = new ComparableVersion(
				"0.0.0-SNAPSHOT");
		for (String directory : directories) {
			ComparableVersion somePastVersion = new ComparableVersion(directory);
			// look for the highest version that is not the current
			if (somePastVersion.compareTo(previousVersion) > 0
					&& somePastVersion.compareTo(currentVersion) != 0) {
				previousVersion = somePastVersion;
			}
		}
		return previousVersion;
	}

}