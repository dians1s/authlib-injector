/*
 * Copyright (C) 2024 BeastMine
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package moe.yushi.authlibinjector;

import static moe.yushi.authlibinjector.util.Logging.log;
import static moe.yushi.authlibinjector.util.Logging.Level.INFO;
import static moe.yushi.authlibinjector.util.Logging.Level.WARNING;

public final class ParentProcessWatcher {

	private static final String PID_ENV = "BEASTMINE_LAUNCHER_PID";
	private static final long CHECK_INTERVAL_MS = 3000;
	private static final long INITIAL_DELAY_MS = 5000;

	private ParentProcessWatcher() {}

	public static void start() {
		String pidStr = System.getenv(PID_ENV);
		if (pidStr == null || pidStr.isEmpty()) {
			log(INFO, "[BeastMine] No " + PID_ENV + " env set, watcher disabled");
			return;
		}

		long launcherPid;
		try {
			launcherPid = Long.parseLong(pidStr);
		} catch (NumberFormatException e) {
			log(WARNING, "[BeastMine] Invalid launcher PID: " + pidStr);
			return;
		}

		log(INFO, "[BeastMine] Parent process watcher started, launcher PID: " + launcherPid);

		Thread watcher = new Thread(() -> {
			try {
				Thread.sleep(INITIAL_DELAY_MS);
			} catch (InterruptedException e) {
				return;
			}

			while (true) {
				try {
					if (!isProcessAlive(launcherPid)) {
						log(WARNING, "[BeastMine] Launcher process " + launcherPid + " is dead, exiting game");
						System.exit(0);
					}
					Thread.sleep(CHECK_INTERVAL_MS);
				} catch (InterruptedException e) {
					break;
				} catch (Exception e) {
					log(WARNING, "[BeastMine] Watcher error: " + e.getMessage());
				}
			}
		}, "BeastMine-ParentWatcher");

		watcher.setDaemon(true);
		watcher.start();
	}

	private static boolean isProcessAlive(long pid) {
		try {
			String os = System.getProperty("os.name").toLowerCase();
			ProcessBuilder pb;
			if (os.contains("win")) {
				pb = new ProcessBuilder("cmd", "/c", "tasklist", "/FI", "PID eq " + pid, "/FO", "CSV", "/NH");
			} else {
				pb = new ProcessBuilder("sh", "-c", "kill -0 " + pid + " 2>/dev/null");
			}
			Process p = pb.start();
			int exitCode = p.waitFor();
			if (os.contains("win")) {
				java.util.Scanner s = new java.util.Scanner(p.getInputStream()).useDelimiter("\\A");
				String output = s.hasNext() ? s.next() : "";
				s.close();
				return output.contains("\"" + pid + "\"");
			}
			return exitCode == 0;
		} catch (Exception e) {
			return true; // can't check, assume alive
		}
	}
}
