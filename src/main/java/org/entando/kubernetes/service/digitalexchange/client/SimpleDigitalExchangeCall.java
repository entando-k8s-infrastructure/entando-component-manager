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
package org.entando.kubernetes.service.digitalexchange.client;

import org.entando.kubernetes.service.digitalexchange.model.ResilientListWrapper;
import org.entando.web.response.EntandoEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

import java.util.Map;

/**
 * Provides the logic for combining a set of SimpleRestResponse retrieved from
 * DE instances.
 */
public class SimpleDigitalExchangeCall<T> extends DigitalExchangeCall<EntandoEntity<T>, ResilientListWrapper<T>> {

    public SimpleDigitalExchangeCall(HttpMethod method,
                                     ParameterizedTypeReference<EntandoEntity<T>> parameterizedTypeReference, String... urlSegments) {
        super(method, parameterizedTypeReference, urlSegments);
    }

    @Override
    protected EntandoEntity<T> getEmptyRestResponse() {
        return new EntandoEntity<>(null);
    }

    @Override
    protected ResilientListWrapper<T> combineResults(Map<String, EntandoEntity<T>> results) {
        ResilientListWrapper<T> wrapper = new ResilientListWrapper<>();
        results.values().forEach(wrapper::addValueFromResponse);
        return wrapper;
    }

    @Override
    protected boolean isResponseParsable(EntandoEntity<T> response) {
        return super.isResponseParsable(response) && response.getPayload() != null;
    }
}
