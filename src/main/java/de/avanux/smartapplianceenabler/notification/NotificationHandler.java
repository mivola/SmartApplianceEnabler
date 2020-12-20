/*
 * Copyright (C) 2020 Axel Müller <axel.mueller@avanux.de>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package de.avanux.smartapplianceenabler.notification;

import de.avanux.smartapplianceenabler.appliance.ApplianceIdConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class NotificationHandler implements ApplianceIdConsumer {
    public static final String CONFIGURATION_KEY_NOTIFICATION_COMMAND = "Notification.Commmand";
    private static final int MAX_ERRORS_PER_DAY = 3;

    private transient Logger logger = LoggerFactory.getLogger(NotificationHandler.class);

    private String applianceId;
    private String command;
    private String senderId;
    private Notifications requestedNotifications = null;
    private int errorCountPerDay = 0;
    private LocalDate errorDate;

    public NotificationHandler(String applianceId, String command, String senderId) {
        this.applianceId = applianceId;
        this.command = command;
        this.senderId = senderId;
        logger.debug("{}: command={} senderId={}", applianceId, command, senderId);
    }

    @Override
    public void setApplianceId(String applianceId) {
        this.applianceId = applianceId;
    }

    public void setRequestedNotifications(Notifications requestedNotifications) {
        this.requestedNotifications = requestedNotifications;
        if(requestedNotifications != null) {
            if(requestedNotifications.getTypes() != null) {
                logger.debug("{}: enabled notifications {}", applianceId, requestedNotifications.getTypes());
            }
            else {
                logger.debug("{}: all notifications enabled", applianceId);
            }
        }
    }

    protected boolean isRequestedNotification(NotificationType type) {
        return this.requestedNotifications != null
                && (this.requestedNotifications.getTypes() == null
                || this.requestedNotifications.getTypes().contains(type.name()));
    }

    private boolean isErrorNotification(NotificationType type) {
        return type.name().contains("ERROR");
    }

    private boolean shouldSendErrorNotification() {
        if(errorCountPerDay == 0) {
            errorDate = LocalDateTime.now().toLocalDate();
        }
        else if(!errorDate.equals(LocalDateTime.now().toLocalDate())) {
            errorCountPerDay = 0;
        }
        errorCountPerDay++;
        return errorCountPerDay <= MAX_ERRORS_PER_DAY;
    }

    public void sendNotification(NotificationType type) {
        if(isRequestedNotification(type)) {
            if(!isErrorNotification(type) || shouldSendErrorNotification()) {
                ResourceBundle messages = ResourceBundle.getBundle("messages", new Locale("de", "DE"));
                String message = messages.getString(type.name());
                try {
                    logger.debug("{}: Executing notification command: {} errorCountPerDay={}", applianceId, command, errorCountPerDay);
                    ProcessBuilder builder = new ProcessBuilder(
                            command,
                            senderId != null ? senderId : applianceId,
                            type.name(),
                            message);
                    Process p = builder.start();
                    int rc = p.waitFor();
                    logger.debug("{}: Notification command exited with return code {}", applianceId, rc);
                } catch (Exception e) {
                    logger.error("{}: Error executing notification command {}", applianceId, command, e);
                }
            }
        }
    }
}
