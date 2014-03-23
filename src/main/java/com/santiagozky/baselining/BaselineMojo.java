package com.santiagozky.baselining;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

import aQute.bnd.differ.Baseline;
import aQute.bnd.differ.Baseline.BundleInfo;
import aQute.bnd.differ.DiffPluginImpl;
import aQute.bnd.differ.Baseline.Info;
import aQute.bnd.osgi.Jar;
import aQute.bnd.version.Version;

@Mojo(name = "baseline", defaultPhase = LifecyclePhase.VERIFY)
@Execute(phase = LifecyclePhase.VERIFY, goal = "baseline")
public class BaselineMojo extends AbstractMojo {
	private static final String EXTENSION = ".jar";

	private static final String ARTIFACT_DESCRIPTION = "groupId:artifactId:[0,version)";

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
	private boolean strict = false;

	@Component
	private RepositorySystem repoSystem;

	@Parameter(defaultValue = "${repositorySystemSession}")
	private RepositorySystemSession repoSession;

	@Parameter(defaultValue = "${project.remoteProjectRepositories}")
	private List<RemoteRepository> projectRepos;

	public void execute() throws MojoExecutionException, MojoFailureException {

		File oldJar = getLastArtifact();

		// new jar comes from the target directory, freshly compiled
		File newJar = new File(target, jarName.concat(EXTENSION));

		getLog().info("Comparing artifact against " + oldJar.getName());
		getLog().info("strict mode is "+strict);

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline;
		try {
			baseline = new Baseline(new SimpleReporter(), differ);
			Jar old = new Jar(oldJar);
			Jar current = new Jar(newJar);

			Set<Info> infos;
			try {
				infos = baseline.baseline(current, old, null);
			} catch (Exception e) {
				throw new MojoExecutionException("could not calculate diff between artifacts",e);
			}
			for (Info info : infos) {
				Version v = info.suggestedVersion;
				getLog().info(
						"package " + info.packageName
								+ " version changed from " + info.olderVersion
								+ " to " + info.newerVersion);
				if (info.mismatch) {
					getLog().error(
							"package "
									+ info.packageName
									+ " version is incorrect. Version should be at least "
									+ v);
					if (strict) {
						throw new MojoFailureException(
								"wrong version for package " + info.packageName);
					}
					
				}
				if (info.warning != null && info.warning.length() > 0) {
					getLog().warn(
							"package " + info.packageName + ": " + info.warning);
				}

			}
			BundleInfo binfo = baseline.getBundleInfo();
			getLog().warn(
					"Bundle version is " + binfo.version
							+ ". The recommended version is "
							+ binfo.suggestedVersion);
			if (binfo.mismatch) {
				if (strict) {
					throw new MojoFailureException("wrong version for artifact");
				}
				
			}

		} catch (IOException e) {
			throw new MojoExecutionException("could not calculate  versions", e);
		} 

	}

	/**
	 * gets the file for the last released artifact.
	 * 
	 * @return
	 * @throws MojoExecutionException
	 */
	private File getLastArtifact() throws MojoExecutionException {
		org.eclipse.aether.version.Version v = getLastVersion();
		return getArtifactFile(v.toString());

	}

	/**
	 * gets the file for the artifact at the specified version.
	 * 
	 * @param version
	 * @return
	 * @throws MojoExecutionException
	 */
	private File getArtifactFile(String version) throws MojoExecutionException {

		Artifact artifactQuery = new DefaultArtifact(groupId.concat(":")
				.concat(name).concat(":").concat(version));
		ArtifactRequest request = new ArtifactRequest(artifactQuery,
				projectRepos, null);
		List<ArtifactRequest> arts = new ArrayList<ArtifactRequest>();
		arts.add(request);
		try {
			ArtifactResult a = repoSystem.resolveArtifact(repoSession, request);
			return a.getArtifact().getFile();
		} catch (ArtifactResolutionException e) {
			throw new MojoExecutionException(
					"could not resolve artifact to compare with", e);
		}

	}

	/**
	 * gets the last version of the current artifact, not including the current
	 * one.
	 * 
	 * @return
	 * @throws MojoExecutionException
	 */
	private org.eclipse.aether.version.Version getLastVersion()
			throws MojoExecutionException {

		// build the artifact description with version range from 0 up to (non
		// inclusive) current version

		String artifactDescription = ARTIFACT_DESCRIPTION.replace("groupId",
				groupId);
		artifactDescription = artifactDescription.replace("artifactId", name);
		artifactDescription = artifactDescription.replace("version", version);

		Artifact artifact = new DefaultArtifact(artifactDescription);

		VersionRangeRequest rangeRequest = new VersionRangeRequest();
		rangeRequest.setArtifact(artifact);
		rangeRequest.setRepositories(projectRepos);

		VersionRangeResult rangeResult;
		try {
			rangeResult = repoSystem.resolveVersionRange(repoSession,
					rangeRequest);
			List<org.eclipse.aether.version.Version> versions = rangeResult
					.getVersions();

			org.eclipse.aether.version.Version lastVersion = versions
					.get(versions.size() - 1);
			return lastVersion;

		} catch (VersionRangeResolutionException e) {
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