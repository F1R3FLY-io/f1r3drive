package io.f1r3fly.f1r3drive.blockchain.client.grcp.listener;

import casper.ExternalCommunicationServiceCommon;
import casper.v1.ExternalCommunicationServiceV1;

public interface UpdateNotificationHandler {
    ExternalCommunicationServiceV1.UpdateNotificationResponse handle(ExternalCommunicationServiceCommon.UpdateNotification notification);
}
