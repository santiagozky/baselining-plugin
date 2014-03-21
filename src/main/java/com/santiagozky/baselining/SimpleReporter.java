package com.santiagozky.baselining;

import java.util.List;

import aQute.service.reporter.Reporter;

public class SimpleReporter implements Reporter {

	public List<String> getWarnings() {
		// TODO Auto-generated method stub
		return null;
	}

	public List<String> getErrors() {
		// TODO Auto-generated method stub
		return null;
	}

	public Location getLocation(String msg) {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isOk() {
		// TODO Auto-generated method stub
		return false;
	}

	public SetLocation error(String format, Object... args) {
		// TODO Auto-generated method stub
		return null;
	}

	public SetLocation warning(String format, Object... args) {
		// TODO Auto-generated method stub
		return null;
	}

	public void trace(String format, Object... args) {
		// TODO Auto-generated method stub

	}

	public void progress(float progress, String format, Object... args) {
		// TODO Auto-generated method stub

	}

	public SetLocation exception(Throwable t, String format, Object... args) {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isPedantic() {
		return true;
	}

}
