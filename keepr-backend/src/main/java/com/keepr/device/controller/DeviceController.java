package com.keepr.device.controller;

import java.util.List;

import com.keepr.common.security.KeeprPrincipal;
import com.keepr.device.dto.CreateDeviceRequest;
import com.keepr.device.dto.DeviceResponse;
import com.keepr.device.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for device management endpoints.
 * All operations are scoped to the authenticated user's household.
 */
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * Creates a new device in the authenticated user's household.
     *
     * @param request   the device creation request
     * @param principal the authenticated user's principal from JWT
     * @return the created device response
     */
    @PostMapping
    public ResponseEntity<DeviceResponse> createDevice(
            @RequestBody CreateDeviceRequest request,
            @AuthenticationPrincipal KeeprPrincipal principal) {
        DeviceResponse response = deviceService.createDevice(request, principal);
        return ResponseEntity.ok(response);
    }

    /**
     * Lists all devices belonging to the authenticated user's household.
     *
     * @param principal the authenticated user's principal from JWT
     * @return list of device responses
     */
    @GetMapping
    public ResponseEntity<List<DeviceResponse>> listDevices(
            @AuthenticationPrincipal KeeprPrincipal principal) {
        List<DeviceResponse> devices = deviceService.listDevices(principal);
        return ResponseEntity.ok(devices);
    }
}
