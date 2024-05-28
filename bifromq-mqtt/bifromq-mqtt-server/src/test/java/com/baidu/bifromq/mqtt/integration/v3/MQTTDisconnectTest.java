/*
 * Copyright (c) 2024. The BifroMQ Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.baidu.bifromq.mqtt.integration.v3;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

import com.baidu.bifromq.mqtt.integration.MQTTTest;
import com.baidu.bifromq.mqtt.integration.v3.client.MqttTestClient;
import com.baidu.bifromq.plugin.authprovider.type.MQTT3AuthData;
import com.baidu.bifromq.plugin.authprovider.type.MQTT3AuthResult;
import com.baidu.bifromq.plugin.authprovider.type.Ok;
import com.baidu.bifromq.plugin.eventcollector.Event;
import com.baidu.bifromq.plugin.eventcollector.EventType;
import com.baidu.bifromq.plugin.eventcollector.mqttbroker.clientdisconnect.ByClient;
import java.util.concurrent.CompletableFuture;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

public class MQTTDisconnectTest extends MQTTTest {
    @Test(groups = "integration")
    public void disconnectDirectly() {
        when(authProvider.auth(any(MQTT3AuthData.class)))
            .thenReturn(CompletableFuture.completedFuture(MQTT3AuthResult.newBuilder()
                .setOk(Ok.newBuilder()
                    .setTenantId(tenantId)
                    .setUserId("testUser")
                    .build()).build()));

        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setUserName("abcdef/testClient");
        MqttTestClient mqttClient = new MqttTestClient(BROKER_URI, "mqtt_client_test");
        mqttClient.connect(connOpts);
        assertTrue(mqttClient.isConnected());
        mqttClient.closeForcibly();
        await().until(() -> !mqttClient.isConnected());
        ArgumentCaptor<Event<?>> argCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventCollector, atLeast(2)).report(argCaptor.capture());
        Event<?> event = argCaptor.getAllValues().get(argCaptor.getAllValues().size() - 1);
        assertTrue(event.type() == EventType.BY_CLIENT && ((ByClient) event).withoutDisconnect());
    }

    @Test(groups = "integration")
    public void disconnect() {
        when(authProvider.auth(any(MQTT3AuthData.class)))
            .thenReturn(CompletableFuture.completedFuture(MQTT3AuthResult.newBuilder()
                .setOk(Ok.newBuilder()
                    .setTenantId(tenantId)
                    .setUserId("testUser")
                    .build()).build()));

        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setUserName("abcdef/testClient");
        MqttTestClient mqttClient = new MqttTestClient(BROKER_URI, "mqtt_client_test");
        mqttClient.connect(connOpts);
        assertTrue(mqttClient.isConnected());
        mqttClient.disconnect();
        await().until(() -> !mqttClient.isConnected());
        ArgumentCaptor<Event<?>> argCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventCollector, atLeast(2)).report(argCaptor.capture());
        Event<?> event = argCaptor.getAllValues().get(argCaptor.getAllValues().size() - 1);
        assertTrue(event.type() == EventType.BY_CLIENT && !((ByClient) event).withoutDisconnect());
    }

}
