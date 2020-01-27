
/*
 * Copyright 2019-Present Entando Inc. (http://www.entando.com) All rights reserved.
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
package org.entando.kubernetes.controller.digitalexchange.job;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import org.entando.kubernetes.model.digitalexchange.DigitalExchangeJob;
import org.entando.kubernetes.security.Roles;
import org.entando.kubernetes.model.web.response.PagedRestResponse;
import org.entando.kubernetes.model.web.response.SimpleRestResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Api(tags = {"digital-exchange", "installation"})
@RequestMapping(value = "/components")
public interface DigitalExchangeInstallResource {

    @ApiOperation(value = "Starts component installation job")
    @ApiResponses({
        @ApiResponse(code = 201, message = "Created")
    })
    @Secured(Roles.INSTALL)
    @PostMapping(value = "/install/{component}", produces = MediaType.APPLICATION_JSON_VALUE)
    SimpleRestResponse<DigitalExchangeJob> install(
           @PathVariable("component") String componentId,
           @RequestParam(name = "version", required = true, defaultValue = "latest") String version);

    @ApiOperation(value = "Starts component remove job ")
    @ApiResponses({
            @ApiResponse( code = 201, message = "Created")
    })
    @Secured(Roles.INSTALL)
    @PostMapping(value = "/uninstall/{component}", produces = MediaType.APPLICATION_JSON_VALUE)
    SimpleRestResponse<DigitalExchangeJob> uninstall(@PathVariable("component") String componentId,
                                                     HttpServletRequest request) throws URISyntaxException ;

    @ApiOperation(value = "Checks installation job status")
    @ApiResponses({
        @ApiResponse(code = 200, message = "OK")
    })
    @Secured(Roles.INSTALL)
    @GetMapping(value = "/install/{component}", produces = MediaType.APPLICATION_JSON_VALUE)
    SimpleRestResponse<DigitalExchangeJob> getLastInstallJob(@PathVariable("component") String componentId);

    @ApiOperation(value = "Checks removal job status")
    @ApiResponses({
            @ApiResponse(code = 200, message = "OK")
    })
    @Secured(Roles.INSTALL)
    @GetMapping(value = "/uninstall/{component}", produces = MediaType.APPLICATION_JSON_VALUE)
    SimpleRestResponse<DigitalExchangeJob> getLastUninstallJob(@PathVariable("component") String componentId);

    @ApiOperation(value = "Get all jobs for a component")
    @ApiResponses({
           @ApiResponse(code = 200, message = "OK")
    })
    @GetMapping(value = "/jobs/{component}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PagedRestResponse<DigitalExchangeJob>> getComponentJobs(@PathVariable("component") String componentId);

}
