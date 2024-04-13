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

package com.baidu.bifromq.plugin.settingprovider;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import lombok.SneakyThrows;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SettingTest {
    private final String tenantId = "tenantA";

    @Mock
    private ISettingProvider provider;

    private AutoCloseable closeable;

    @BeforeMethod
    public void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        System.setProperty("setting_refresh_seconds", "1");
    }

    @SneakyThrows
    @AfterMethod
    public void tearDown() {
        closeable.close();
    }

    @Test
    public void enumInitialValue() {
        for (Setting setting : Setting.values()) {
            assertTrue(setting.isValid(setting.current(tenantId)));
        }
    }

    @Test
    public void provideValue() {
        Setting.MaxTopicFiltersPerInbox.setProvider(provider);
        when(provider.provide(Setting.MaxTopicFiltersPerInbox, tenantId)).thenReturn(200);

        assertEquals((int) Setting.MaxTopicFiltersPerInbox.current(tenantId), 200);
    }

    @Test
    public void systemPropertyOverride() {
        System.setProperty("MsgPubPerSec", "100");
        assertEquals((int) Setting.MsgPubPerSec.resolve(200), 100);

        // invalid value should be ignored
        System.setProperty("MsgPubPerSec", "sdfa");
        assertEquals((int) Setting.MsgPubPerSec.resolve(200), 200);
    }
}
