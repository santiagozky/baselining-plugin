package com.santiagozky.baselining;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import aQute.bnd.differ.Baseline.Info;
import aQute.bnd.differ.DiffPluginImpl;
import aQute.bnd.osgi.Jar;
import aQute.bnd.version.Version;

@Mojo(name = "baseline", defaultPhase = LifecyclePhase.VERIFY)
@Execute(phase = LifecyclePhase.VERIFY, goal = "baseline")
public class BaselineMojo extends AbstractMojo {
	//TODO: how to get the real extension?
	private static final String EXTENSION = ".jar";

	private static final String ARTIFACT_DESCRIPTION = "groupId:artifactId:[0,version)";

	/**
	 * if strict is true, the build will fail if a package is on a lower version
	 * than recommended.
	 */
	@Parameter
	private boolean strict = false;

	/**
	 * Always show the suggested version of a package.
	 * By default it will only shows it if the suggested is higher than the actual.
	 */
	@Parameter
	private boolean verbose= false;


	// these should not be modified

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

	// aether stuff

	@Component
	private RepositorySystem repoSystem;

	@Parameter(defaultValue = "${repositorySystemSession}")
	private RepositorySystemSession repoSession;

	@Parameter(defaultValue = "${project.remoteProjectRepositories}")
	private List<RemoteRepository> projectRepos;

	public void execute() throws MojoExecutionException, MojoFailureException {
		
		

		File oldJar = getLastArtifact();

	
		File newJar = getCurrentArtifact();

		getLog().info(String.format("Comparing artifact %s against %s",newJar.getName(), oldJar.getName()));
		getLog().debug("strict mode is " + strict);
		getLog().debug("verbose mode is " + verbose);
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
				throw new MojoExecutionException(
						"could not calculate generate package differential", e);
			}
			for (Info info : infos) {
				Version v = info.suggestedVersion;
				
			
				if(verbose){
					getLog().info(String.format("package %s version changed from %s to %s",info.packageName,info.olderVersion,info.newerVersion));
					getLog().info(String.format("package %s suggested version is %s",info.packageName,v));
				}
				if (info.mismatch) {
					getLog().error(String.format("package %s version is incorrect. Should be at least %s", info.packageName,v));
					if (strict) {
						throw new MojoFailureException(
								String.format("package %s version should be at least %s " ,info.packageName,v));
					}

				}
				if (info.warning != null && info.warning.length() > 0) {
					getLog().warn(String.format("package %s : %s", info.packageName,info.warning));			
				}

			}
			BundleInfo binfo = baseline.getBundleInfo();
			getLog().warn(String.format("Bundle version is %s, the recommended version is %s",binfo.version,binfo.suggestedVersion));
			if (binfo.mismatch) {
				if (strict) {
					throw new MojoFailureException("wrong version for artifact");
				}

			}

		} catch (IOException e) {
			throw new MojoExecutionException("could not calculate  versions", e);
		}

	}

	private File getCurrentArtifact() {
		// new jar comes from the target directory, freshly compiled
		return new File(target, jarName.concat(EXTENSION));
	}

	/**
	 * gets the file for the last released artifact.
	 * 
	 * @return
	 * @throws MojoExecutionException
	 */
	private File getLastArtifact() throws MojoExecutionException {
		org.eclipse.aether.version.Version v = getLastVersion();

		Artifact artifactQuery = new DefaultArtifact(groupId.concat(":")
				.concat(name).concat(":").concat(v.toString()));
		getLog().debug(String.format("looking for artifact %s", artifactQuery.toString()));
		return getArtifactFile(artifactQuery);

	}


	/**
	 * gets the file for the artifact at the specified version.
	 * 
	 * @param version
	 * @return
	 * @throws MojoExecutionException
	 */
	private File getArtifactFile(Artifact artifactQuery)
			throws MojoExecutionException {

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
		getLog().info(String.format("searching for artifacts in range %s", artifactDescription));
		VersionRangeRequest rangeRequest = new VersionRangeRequest();
		rangeRequest.setArtifact(artifact);
		rangeRequest.setRepositories(projectRepos);

		VersionRangeResult rangeResult;
		try {
			rangeResult = repoSystem.resolveVersionRange(repoSession,
					rangeRequest);
			List<org.eclipse.aether.version.Version> versions = rangeResult
					.getVersions();
			getLog().debug(String.format("found versions %s",rangeResult.getVersions()));
			org.eclipse.aether.version.Version lastVersion = versions
					.get(versions.size() - 1);
			getLog().debug(String.format("previous version is %s",lastVersion));
			return lastVersion;

		} catch (VersionRangeResolutionException e) {
			throw new MojoExecutionException("could not calculate  versions", e);
		}

	}

}