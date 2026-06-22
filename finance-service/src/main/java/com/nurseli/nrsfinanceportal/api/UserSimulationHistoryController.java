package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.simulation.SimulationHistoryEntryDto;
import com.nurseli.nrsfinanceportal.api.dto.simulation.SimulationHistoryListResponse;
import com.nurseli.nrsfinanceportal.api.dto.simulation.SimulationHistorySaveRequest;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.application.simulation.UserSimulationHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Kullanıcı simülasyon geçmişi CRUD endpoint'leri.
 */
@RestController
@RequestMapping({"/api/v1/me/simulation-history", "/api/me/simulation-history"})
@RequiredArgsConstructor
public class UserSimulationHistoryController {

    private final UserSimulationHistoryService userSimulationHistoryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<SimulationHistoryListResponse> listMine() {
        return ApiResponse.success(userSimulationHistoryService.listForCurrentUser());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<SimulationHistoryEntryDto> getById(@PathVariable String id) {
        return ApiResponse.success(userSimulationHistoryService.getByIdForCurrentUser(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<SimulationHistoryEntryDto> save(@Valid @RequestBody SimulationHistorySaveRequest request) {
        return ApiResponse.success(userSimulationHistoryService.saveForCurrentUser(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable String id) {
        userSimulationHistoryService.deleteForCurrentUser(id);
        return ApiResponse.success(null);
    }
}
