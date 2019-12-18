/*
 * Copyright 2018-Present Entando Inc. (http://www.entando.com) All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package org.entando.kubernetes.digitalexchange.category;

import com.agiletec.aps.system.services.baseconfig.ConfigInterface;
import org.entando.entando.aps.system.DigitalExchangeConstants;
import org.entando.entando.aps.system.services.digitalexchange.model.ResilientListWrapper;
import org.entando.entando.web.common.model.SimpleRestResponse;
import org.entando.kubernetes.digitalexchange.client.DigitalExchangesClientMocker;
import org.entando.kubernetes.service.digitalexchange.category.DigitalExchangeCategoriesService;
import org.entando.kubernetes.service.digitalexchange.category.DigitalExchangeCategoriesServiceImpl;
import org.entando.kubernetes.client.digitalexchange.DigitalExchangesClient;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.entando.entando.aps.system.services.digitalexchange.DigitalExchangeTestUtils.*;
import static org.entando.entando.aps.system.services.digitalexchange.DigitalExchangeTestUtils.DE_1_ID;
import static org.entando.entando.aps.system.services.digitalexchange.DigitalExchangeTestUtils.DE_2_ID;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DigitalExchangeCategoriesServiceTest {

    private DigitalExchangesClientMocker clientMocker;
    private DigitalExchangesClient client;
    private DigitalExchangeCategoriesService service;

    @Before
    public void setUp() {
        initClientMocks();
        service = new DigitalExchangeCategoriesServiceImpl(clientMocker.getMessageSource(), client);
    }

    @Test
    public void shouldGetCategories() {

        when(configManager.getConfigItem(DigitalExchangeConstants.CONFIG_ITEM_DIGITAL_EXCHANGE_CATEGORIES))
                .thenReturn("widget,pageModel,component,fragment,api,contentModel,contentType");

        ResilientListWrapper<String> result = service.getCategories();

        assertTrue(result.getErrors().isEmpty());
        assertEquals(3, result.getList().size());
        assertThat(result.getList()).containsExactly("widget", "pageModel", "fragment");
    }

    @Test
    public void shouldReturnErrorForMissingCategoriesConfig() {

        when(configManager.getConfigItem(DigitalExchangeConstants.CONFIG_ITEM_DIGITAL_EXCHANGE_CATEGORIES))
                .thenReturn(null);

        ResilientListWrapper<String> result = service.getCategories();

        assertEquals(1, result.getErrors().size());
        assertTrue(result.getList().isEmpty());
    }

    private void initClientMocks() {
        List<String> de1Categories = Arrays.asList("pageModel", "fragment", "unsupportedType1");
        List<String> de2Categories = Arrays.asList("pageModel", "widget", "unsupportedType2");

        clientMocker = new DigitalExchangesClientMocker();
        clientMocker.getDigitalExchangesMocker()
                .addDigitalExchange(DE_1_ID, new SimpleRestResponse<>(de1Categories))
                .addDigitalExchange(DE_2_ID, new SimpleRestResponse<>(de2Categories));

        client = clientMocker.build();
    }
}
