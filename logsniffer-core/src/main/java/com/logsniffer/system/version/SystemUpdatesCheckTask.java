package com.logsniffer.system.version;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.logsniffer.system.notification.Notification;
import com.logsniffer.system.notification.Notification.Level;
import com.logsniffer.system.notification.Notification.Type;
import com.logsniffer.system.notification.NotificationProvider;
import com.logsniffer.system.version.UpdatesInfoProvider.UpdatesInfoContext;
import com.logsniffer.util.value.ConfigValue;
import com.logsniffer.util.value.Configured;

/**
 * Checks for updates and creates a notification periodically.
 * 
 * @author mbok
 *
 */
@Component
public class SystemUpdatesCheckTask {
	public static final String PROP_LOGSNIFFER_UPDATES_CHECK_ENABLED = "logsniffer.system.updatesCheckEnabled";

	private Logger logger = LoggerFactory.getLogger(getClass());

	@Configured(value = PROP_LOGSNIFFER_UPDATES_CHECK_ENABLED, defaultValue = "true")
	private ConfigValue<Boolean> updatesCheckEnabled;

	@Autowired
	private UpdatesInfoProvider updatesProvider;

	@Autowired
	private NotificationProvider notificationProvider;

	@Autowired
	private VelocityEngine velocityEngine;

	@Value(value = "${logsniffer.version}")
	private String currentVersion;

	@Scheduled(fixedDelay = 1000 * 60 * 60 * 24, initialDelay = 60000)
	public void checkForUpdates() {
		if (!updatesCheckEnabled.get()) {
			logger.debug("Updates check disabled by configuration");
			return;
		}
		try {
			UpdatesInfoContext context = new UpdatesInfoContext() {
				@Override
				public String getCurrentVersion() {
					return currentVersion;
				}
			};
			logger.debug("Checking for system updates, current version: {}", context.getCurrentVersion());
			VersionInfo latestStableVersion = updatesProvider.getLatestStableVersion(context);
			if (latestStableVersion.compareTo(new VersionInfo(context.getCurrentVersion())) > 0) {
				logger.debug("System update available: {}", latestStableVersion);
				VelocityContext vcontext = new VelocityContext();
				vcontext.put("version", latestStableVersion);
				vcontext.put("context", context);
				StringWriter titleWriter = new StringWriter();
				velocityEngine.evaluate(vcontext, titleWriter, "TemplateName",
						new InputStreamReader(
								getClass().getResourceAsStream("/snippets/system/systemUpdatesNotificationTitle.html"),
								"UTF-8"));
				StringWriter bodyWriter = new StringWriter();
				velocityEngine.evaluate(vcontext, bodyWriter, "TemplateName",
						new InputStreamReader(
								getClass().getResourceAsStream("/snippets/system/systemUpdatesNotificationBody.html"),
								"UTF-8"));
				Notification n = new Notification();
				n.setId("/system/updateAvailable/" + latestStableVersion.getName());
				n.setTitle(titleWriter.toString());
				n.setMessage(bodyWriter.toString());
				n.setLevel(Level.INFO);
				n.setType(Type.TOPIC);
				notificationProvider.store(n, false);
			} else {
				logger.debug("System is up to date, got latest stable version: {}", latestStableVersion);
			}
		} catch (IOException e) {
			logger.info("Failed to check for system updates", e);
		}
	}

}
