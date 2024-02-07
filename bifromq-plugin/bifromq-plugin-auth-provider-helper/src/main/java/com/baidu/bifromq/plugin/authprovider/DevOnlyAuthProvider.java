/*
 * Copyright (c) 2023. The BifroMQ Authors. All Rights Reserved.
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

package com.baidu.bifromq.plugin.authprovider;

import com.baidu.bifromq.plugin.authprovider.type.CheckResult;
import com.baidu.bifromq.plugin.authprovider.type.MQTT3AuthData;
import com.baidu.bifromq.plugin.authprovider.type.MQTT3AuthResult;
import com.baidu.bifromq.plugin.authprovider.type.MQTTAction;
import com.baidu.bifromq.plugin.authprovider.type.Ok;
import com.baidu.bifromq.type.ClientInfo;
import com.google.common.base.Strings;
import java.util.concurrent.CompletableFuture;

class DevOnlyAuthProvider implements IAuthProvider {
    private static final CheckResult GRANTED = CheckResult.newBuilder().setGranted(
        com.baidu.bifromq.plugin.authprovider.type.Granted.getDefaultInstance()).build();

    @Override
    public CompletableFuture<MQTT3AuthResult> auth(MQTT3AuthData authData) {
        if (!Strings.isNullOrEmpty(authData.getUsername())) {
            String[] username = authData.getUsername().split("/");
            if (username.length == 2) {
                return CompletableFuture.completedFuture(MQTT3AuthResult
                    .newBuilder()
                    .setOk(Ok.newBuilder()
                        .setTenantId(username[0])
                        .setUserId(username[1])
                        .build())
                    .build());
            } else {
                return CompletableFuture.completedFuture(MQTT3AuthResult.newBuilder()
                    .setOk(Ok.newBuilder()
                        .setTenantId("DevOnly")
                        .setUserId("DevUser_" + System.nanoTime()).build())
                    .build());
            }
        } else {
            return CompletableFuture.completedFuture(MQTT3AuthResult.newBuilder()
                .setOk(Ok.newBuilder()
                    .setTenantId("DevOnly")
                    .setUserId("DevUser_" + System.nanoTime()).build())
                .build());
        }
    }

    @Override
    public CompletableFuture<Boolean> check(ClientInfo clientInfo, MQTTAction action) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<CheckResult> checkPermission(ClientInfo client, MQTTAction action) {
        return CompletableFuture.completedFuture(GRANTED);
    }
}