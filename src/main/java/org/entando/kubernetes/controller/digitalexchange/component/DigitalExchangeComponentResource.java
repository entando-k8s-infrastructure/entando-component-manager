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
package org.entando.kubernetes.controller.digitalexchange.component;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.entando.kubernetes.model.debundle.EntandoDeBundle;
import org.entando.kubernetes.security.Roles;
import org.entando.web.request.PagedListRequest;
import org.entando.web.response.PagedRestResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Api(tags = {"digital-exchange", "components"})
@RequestMapping(value = "/components")
public interface DigitalExchangeComponentResource {
    
    @ApiOperation(value = "Returns available Digital Exchange components")
    @ApiResponses({
        @ApiResponse(code = 200, message = "OK")
    })
    @Secured(Roles.LIST_COMPONENTS)
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PagedRestResponse<DigitalExchangeComponent>> getComponents();
}
