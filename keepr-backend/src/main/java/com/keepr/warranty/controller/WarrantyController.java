package com.keepr.warranty.controller;

import com.keepr.common.security.KeeprPrincipal;
import com.keepr.warranty.dto.CreateWarrantyRequest;
import com.keepr.warranty.dto.WarrantyResponse;
import com.keepr.warranty.service.WarrantyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for warranty management endpoints.
 * All operations are scoped to the authenticated user's household.
 */
@RestController
@RequestMapping("/warranties")
@RequiredArgsConstructor
public class WarrantyController {

    private final WarrantyService warrantyService;

    /**
     * Creates a new warranty attached to a device in the authenticated user's household.
     *
     * @param request   the warranty creation request
     * @param principal the authenticated user's principal from JWT
     * @return the created warranty response
     */
    @PostMapping
    public ResponseEntity<WarrantyResponse> createWarranty(
            @RequestBody CreateWarrantyRequest request,
            @AuthenticationPrincipal KeeprPrincipal principal) {
        WarrantyResponse response = warrantyService.createWarranty(request, principal);
        return ResponseEntity.ok(response);
    }
}
